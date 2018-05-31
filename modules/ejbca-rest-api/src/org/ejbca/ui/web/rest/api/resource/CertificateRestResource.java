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

import java.math.BigInteger;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.DatatypeConverter;

import org.cesecore.CesecoreException;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.crl.RevocationReasons;
import org.cesecore.util.CertTools;
import org.cesecore.util.StringTools;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ra.NoSuchEndEntityException;
import org.ejbca.core.model.InternalEjbcaResources;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.era.RaMasterApiProxyBeanLocal;
import org.ejbca.core.model.ra.AlreadyRevokedException;
import org.ejbca.core.model.ra.RevokeBackDateNotAllowedForProfileException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileNotFoundException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileValidationException;
import org.ejbca.core.protocol.rest.EnrollPkcs10CertificateRequest;
import org.ejbca.ui.web.rest.api.exception.RestException;
import org.ejbca.ui.web.rest.api.io.request.EnrollCertificateRestRequest;
import org.ejbca.ui.web.rest.api.io.response.CertificateRestResponse;
import org.ejbca.ui.web.rest.api.io.response.CertificatesRestResponse;
import org.ejbca.ui.web.rest.api.io.response.ExpiringCertificatesResponse;
import org.ejbca.ui.web.rest.api.io.response.PaginationRestResponseComponent;
import org.ejbca.ui.web.rest.api.io.response.RevocationResultRestResponse;

import io.swagger.annotations.Api;

/**
 * JAX-RS resource handling certificate-related requests.
 *
 * @version $Id$
 */
@Api
@Path("v1/certificate")
@Stateless
public class CertificateRestResource extends BaseRestResource {

    /**
     * Internal localization of logs and errors
     */
    private static final InternalEjbcaResources intres = InternalEjbcaResources.getInstance();

    @EJB
    private RaMasterApiProxyBeanLocal raMasterApi;

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    @Override
    public Response status() {
        return super.status();
    }

    @POST
    @Path("/pkcs10enroll")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response enrollPkcs10Certificate(@Context HttpServletRequest requestContext, EnrollCertificateRestRequest enrollcertificateRequest)
            throws RestException, AuthorizationDeniedException {

        try {
            AuthenticationToken authenticationToken = getAdmin(requestContext, false);
            
            EnrollPkcs10CertificateRequest requestDto = new EnrollPkcs10CertificateRequest.Builder()
                    .certificateRequest(enrollcertificateRequest.getCertificateRequest())
                    .certificateProfileName(enrollcertificateRequest.getCertificateProfileName())
                    .endEntityProfileName(enrollcertificateRequest.getEndEntityProfileName())
                    .certificateAuthorityName(enrollcertificateRequest.getCertificateAuthorityName())
                    .username(enrollcertificateRequest.getUsername())
                    .password(enrollcertificateRequest.getPassword())
                    .build();
                    
            byte[] certificate = raMasterApi.createCertificateRest(authenticationToken, requestDto);
            
            X509Certificate cert = CertTools.getCertfromByteArray(certificate, X509Certificate.class);
            
            CertificateRestResponse enrollCertificateRestResponse = CertificateRestResponse.converter().toRestResponse(cert);
            return Response.ok(enrollCertificateRestResponse).build();
        } catch (EjbcaException | EndEntityProfileNotFoundException | CertificateException | EndEntityProfileValidationException | CesecoreException e) {
            throw new RestException(Status.BAD_REQUEST.getStatusCode(), e.getMessage());
        }
    }


    /**
     * Revokes the specified certificate
     *
     * @param requestContext HttpServletRequest
     * @param issuerDN       of the certificate to revoke
     * @param serialNumber   HEX encoded SN with or without 0x prefix
     * @param reason         revocation reason. Must be valid RFC5280 reason: 
     *                          NOT_REVOKED, UNSPECIFIED ,KEYCOMPROMISE,
     *                          CACOMPROMISE, AFFILIATIONCHANGED, SUPERSEDED, CESSATIONOFOPERATION,
     *                          CERTIFICATEHOLD, REMOVEFROMCRL, PRIVILEGESWITHDRAWN, AACOMPROMISE
     * @param date           revocation date (optional). Must be valid ISO8601 date string
     * @return JSON representation of serialNr, revocation status, date and optional message
     * @see org.cesecore.certificates.crl.RevocationReasons
     */
    @PUT
    @Path("/{issuer_dn}/{certificate_serial_number}/revoke")
    @Produces(MediaType.APPLICATION_JSON)
    public Response revokeCertificate(
            @Context HttpServletRequest requestContext,
            @PathParam("issuer_dn") String issuerDN,
            @PathParam("certificate_serial_number") String serialNumber,
            @QueryParam("reason") String reason,
            @QueryParam("date") String date)
            throws AuthorizationDeniedException, RestException, ApprovalException, RevokeBackDateNotAllowedForProfileException, CADoesntExistsException, AlreadyRevokedException,
            NoSuchEndEntityException, WaitingForApprovalException {
        final AuthenticationToken admin = getAdmin(requestContext, false);
        RevocationReasons reasons = RevocationReasons.getFromCliValue(reason);
        // TODO Replace with @ValidRevocationReason
        if (reasons == null) {
            throw new RestException(Response.Status.BAD_REQUEST.getStatusCode(), "Invalid revocation reason.");
        }
        final int revocationReason = reasons.getDatabaseValue();
        final BigInteger serialNr;
        try {
            serialNr = StringTools.getBigIntegerFromHexString(serialNumber);
        } catch (NumberFormatException e) {
            throw new RestException(Response.Status.BAD_REQUEST.getStatusCode(), "Invalid serial number format. Should be "
                    + "HEX encoded (optionally with '0x' prefix) e.g. '0x10782a83eef170d4'");
        }
        Date revocationDate;
        if (date != null) {
            revocationDate = getValidatedRevocationDate(date);
        } else {
            revocationDate = new Date();
        }
        raMasterApi.revokeCert(admin, serialNr, revocationDate, issuerDN, revocationReason, false);
        final RevocationResultRestResponse result = new RevocationResultRestResponse(serialNr, revocationDate, RevocationResultRestResponse.STATUS_REVOKED, "Successfully revoked");
        return Response.ok(result).build();
    }

    // TODO Replace with @ValidRevocationDate annotation
    private Date getValidatedRevocationDate(String sDate) throws RestException {
        Date date = null;
        if (sDate != null) {
            try {
                date = DatatypeConverter.parseDateTime(sDate).getTime();
            } catch (IllegalArgumentException e) {
                throw new RestException(Response.Status.BAD_REQUEST.getStatusCode(), intres.getLocalizedMessage("ra.bad.date", sDate));
            }
            if (date.after(new Date())) {
                throw new RestException(Response.Status.BAD_REQUEST.getStatusCode(), "Revocation date in the future: '" + sDate + "'.");
            }
        }
        return date;
    }

    @GET
    @Path("/expire")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCertificatesAboutToExpire(@Context HttpServletRequest requestContext,
                                                 @QueryParam("days") long days,
                                                 @QueryParam("offset") int offset,
                                                 @QueryParam("maxNumberOfResults") int maxNumberOfResults) throws AuthorizationDeniedException, CertificateEncodingException, RestException {
        if (requestContext == null) {
            throw new RestException(Response.Status.BAD_REQUEST.getStatusCode(), "Missing request context");
        }
        final AuthenticationToken admin = getAdmin(requestContext, true);
        int count = raMasterApi.getCountOfCertificatesByExpirationTime(admin, days);
        List<Certificate> expiringCertificates = raMasterApi.getCertificatesByExpirationTime(admin, days, maxNumberOfResults, offset);
        int processedResults = offset + maxNumberOfResults;
        PaginationRestResponseComponent paginationRestResponseComponent = PaginationRestResponseComponent.builder().setMoreResults(count > processedResults)
                .setNextOffset(offset + maxNumberOfResults)
                .setNumberOfResults(count - processedResults)
                .build();
        CertificatesRestResponse certificatesRestResponse = new CertificatesRestResponse(CertificatesRestResponse.converter().toRestResponses(expiringCertificates));
        ExpiringCertificatesResponse response = new ExpiringCertificatesResponse(paginationRestResponseComponent, certificatesRestResponse);
        return Response.ok(response).build();
    }
}
