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

import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Collection;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.util.CertTools;
import org.cesecore.util.StringTools;
import org.ejbca.core.ejb.rest.EjbcaRestHelperSessionLocal;
import org.ejbca.core.model.era.RaMasterApiProxyBeanLocal;
import org.ejbca.ui.web.rest.api.exception.RestException;
import org.ejbca.ui.web.rest.api.io.response.CaInfoRestResponse;
import org.ejbca.ui.web.rest.api.io.response.CaInfosRestResponse;
import org.ejbca.ui.web.rest.api.io.response.RestResourceStatusRestResponse;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * JAX-RS resource handling CA related requests.
 *
 * @version $Id$
 */
@Api(tags = "v1/ca")
@Path("/v1/ca")
@Produces(MediaType.APPLICATION_JSON)
@Stateless
public class CaRestResource extends BaseRestResource {

    @EJB
    private EjbcaRestHelperSessionLocal ejbcaRestHelperSession;
    @EJB
    private RaMasterApiProxyBeanLocal raMasterApiProxy;

    @GET
    @Path("/status")
    @ApiOperation(value = "Get the status of this RestResource", notes = "Get the status of this RestResource", response = RestResourceStatusRestResponse.class)
    @Override
    public Response status() {
        return super.status();
    }

    /**
     * @param subjectDn CA subjectDn
     * @return PEM file with CA certificates
     */
    @GET
    @Path("/{subject_dn}/certificate/download")
    @Produces(MediaType.WILDCARD)
    @ApiOperation(value = "Get PEM file with CA certificates", notes = "Get PEM file with CA certificates")
    public Response getCertificateAsPem(@Context HttpServletRequest requestContext,
                                        @PathParam("subject_dn") String subjectDn) throws AuthorizationDeniedException, CertificateEncodingException, CADoesntExistsException, RestException {
        final AuthenticationToken admin = getAdmin(requestContext, false);
        subjectDn = CertTools.stringToBCDNString(subjectDn);
        Collection<Certificate> certificateChain = raMasterApiProxy.getCertificateChain(admin, subjectDn.hashCode());

        byte[] bytes = CertTools.getPemFromCertificateChain(certificateChain);
        return Response.ok(bytes)
                .header("Content-disposition", "attachment; filename=\"" + StringTools.stripFilename(subjectDn + ".cacert.pem") + "\"")
                .header("Content-Length", bytes.length)
                .build();
    }

    /**
     * Returns the Response containing the list of CAs with general information per CA as Json.
     *
     * @param httpServletRequest HttpServletRequest of a request.
     *
     * @return The response containing the list of CAs and its general information.
     */
    @GET
    @ApiOperation(value = "Returns the Response containing the list of CAs with general information per CA as Json",
        notes = "Returns the Response containing the list of CAs with general information per CA as Json",
        response = CaInfosRestResponse.class)
    public Response listCas(@Context final HttpServletRequest httpServletRequest) throws AuthorizationDeniedException, CADoesntExistsException, RestException {
        final AuthenticationToken adminToken = getAdmin(httpServletRequest, false);
        List<CaInfoRestResponse> caInfoRestResponseList = CaInfosRestResponse.converter().toRestResponses(raMasterApiProxy.getAuthorizedCAInfos(adminToken));
        final CaInfosRestResponse caInfosRestResponse = CaInfosRestResponse.builder()
                .certificateAuthorities(caInfoRestResponseList)
                .build();
        return Response.ok(caInfosRestResponse).build();
    }

}
