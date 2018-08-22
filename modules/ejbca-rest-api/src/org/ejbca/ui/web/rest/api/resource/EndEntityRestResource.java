/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.ui.web.rest.api.resource;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.log4j.Logger;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.IllegalNameException;
import org.cesecore.certificates.certificate.exception.CertificateSerialNumberException;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.ExtendedInformation;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ra.EndEntityExistsException;
import org.ejbca.core.model.InternalEjbcaResources;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.era.RaEndEntitySearchRequest;
import org.ejbca.core.model.era.RaEndEntitySearchResponse;
import org.ejbca.core.model.era.RaMasterApiProxyBeanLocal;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileValidationException;
import org.ejbca.core.protocol.ws.objects.ExtendedInformationWS;
import org.ejbca.core.protocol.ws.objects.UserDataVOWS;
import org.ejbca.ui.web.rest.api.config.ObjectMapperContextResolver;
import org.ejbca.ui.web.rest.api.exception.RestException;
import org.ejbca.ui.web.rest.api.io.response.CertificateRestResponse;
import org.ejbca.ui.web.rest.api.io.response.EndEntityInfosRestResponse;
import org.ejbca.ui.web.rest.api.io.response.KeystoreRestResponse;
import org.ejbca.ui.web.rest.api.io.response.RestResourceStatusRestResponse;
import org.ejbca.ui.web.rest.api.io.response.RevokeStatusRestResponse;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * JAX-RS resource handling certificate-related requests.
 *
 * @version $Id$
 */
@Api(tags = "v1/ees")
@Path("v1/ees")
@Stateless
public class EndEntityRestResource extends BaseRestResource
{
	private static final Logger LOGGER = Logger.getLogger(EndEntityRestResource.class);

	/**
	 * Internal localization of logs and errors
	 */
	private static final InternalEjbcaResources intres = InternalEjbcaResources.getInstance();

	@EJB
	private RaMasterApiProxyBeanLocal raMasterApi;

	@GET
	@Path("/status")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Get the status of this RestResource", notes = "Get the status of this RestResource", response = RestResourceStatusRestResponse.class)
	@Override
	public Response status()
	{
		return super.status();
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Get end entities", notes = "Get end entities", response = EndEntityInfosRestResponse.class)
	/*
	 * TODO: handle more complex search
	 */
	public Response getEndEntities(@Context final HttpServletRequest requestContext /* , EndEntitySearchRestRequest searchRestRequest */) throws RestException, AuthorizationDeniedException
	{

		// try
		// {
		final AuthenticationToken authenticationToken = getAdmin(requestContext, false);

		final RaEndEntitySearchResponse searchResponse = raMasterApi.searchForEndEntities(authenticationToken, new RaEndEntitySearchRequest());

		// final EndEntityInfosRestResponse endEntitiesResponse = new EndEntityInfosRestResponse();
		// endEntitiesResponse.setEndEntityInfos(searchResponse.getEndEntities());
		return Response.ok(searchResponse).build();
		// }
		// catch (EjbcaException | EndEntityProfileNotFoundException | CertificateException | EndEntityProfileValidationException | CesecoreException e)
		// {
		// throw new RestException(Status.BAD_REQUEST.getStatusCode(), e.getMessage());
		// }
	}

	private static final List<ExtendedInformationWS> DEFAULT_EXT_INFO_LIST = Collections.singletonList(new ExtendedInformationWS(ExtendedInformation.SUBJECTDIRATTRIBUTES, ""));

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Adds an end entity", notes = "Adds an end entity", response = KeystoreRestResponse.class)
	public Response addEndEntity(@Context final HttpServletRequest requestContext, final UserDataVOWS userData) throws AuthorizationDeniedException, RestException
	{
		final AuthenticationToken authenticationToken = getAdmin(requestContext, false);
		/*
		 * FIXME: {@link CertificateRestResource#enrollKeystore(HttpServletRequest, org.ejbca.ui.web.rest.api.io.request.KeyStoreRestRequest)} fails if {@link UserDataVOWS#getExtendedInformation()}
		 * returns empty, so we make sure it is not.
		 */
		final List<ExtendedInformationWS> inputExtInfo = userData.getExtendedInformation();
		if (inputExtInfo == null)
		{
			userData.setExtendedInformation(DEFAULT_EXT_INFO_LIST);
		}
		else if (inputExtInfo.isEmpty())
		{
			inputExtInfo.addAll(DEFAULT_EXT_INFO_LIST);
		}

		boolean isAdded;
		try
		{
			isAdded = raMasterApi.addUserFromWS(authenticationToken, userData, false);
			return Response.ok(isAdded).build();
		}
		catch (EjbcaException | WaitingForApprovalException | EndEntityExistsException | CADoesntExistsException | IllegalNameException | CertificateSerialNumberException
		        | EndEntityProfileValidationException e)
		{
			/*
			 * TODO: better error handling, it may not be only bad request kind of response in this case
			 */
			throw new RestException(Status.BAD_REQUEST.getStatusCode(), e.getMessage(), e);
		}

	}

	@GET
	@Path("/{username}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Get the end entity identified by username", notes = "Get the end entity identified by username", response = CertificateRestResponse.class)
	public Response getEndEntity(@Context final HttpServletRequest requestContext, @PathParam("username") final String username) throws RestException, AuthorizationDeniedException
	{

		final AuthenticationToken authenticationToken = getAdmin(requestContext, false);
		final EndEntityInformation endEntityInfo = raMasterApi.searchUser(authenticationToken, username);
		return Response.ok(endEntityInfo).build();
	}

	@DELETE
	@Path("/{username}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Removes the end entity identified by username", notes = "Removes the end entity identified by username", response = RevokeStatusRestResponse.class)
	public Response removeEndEntity(@Context final HttpServletRequest requestContext, @PathParam("username") final String username) throws AuthorizationDeniedException, RestException
	{
		final AuthenticationToken authenticationToken = getAdmin(requestContext, false);
		raMasterApi.deleteUser(authenticationToken, username);
		return Response.ok().build();
	}

	public static void main(final String... args) throws JsonParseException, JsonMappingException, IOException
	{
		final File jsonFile = new File("/home/darkcyrus/Desktop/DRIVER+/ejbca-rest-test/endEntityInfo.json");

		final ObjectMapperContextResolver provider = new ObjectMapperContextResolver();
		final ObjectMapper mapper = provider.getContext(UserDataVOWS.class);

		// Convert JSON string from file to Object
		final UserDataVOWS info = mapper.readValue(jsonFile, UserDataVOWS.class);
		System.out.println(info);
	}

}
