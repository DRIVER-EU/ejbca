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

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.cesecore.SystemTestsConfiguration;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authentication.tokens.X509CertificateAuthenticationTokenMetaData;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.user.AccessMatchType;
import org.cesecore.authorization.user.matchvalues.X500PrincipalAccessMatchValue;
import org.cesecore.certificates.ca.*;
import org.cesecore.certificates.certificate.CertificateCreateException;
import org.cesecore.certificates.certificate.CertificateRevokeException;
import org.cesecore.certificates.certificate.IllegalKeyException;
import org.cesecore.certificates.certificate.InternalCertificateStoreSessionRemote;
import org.cesecore.certificates.certificate.certextensions.CertificateExtensionException;
import org.cesecore.certificates.certificate.exception.CertificateSerialNumberException;
import org.cesecore.certificates.certificate.exception.CustomCertificateSerialNumberException;
import org.cesecore.certificates.certificate.request.SimpleRequestMessage;
import org.cesecore.certificates.certificate.request.X509ResponseMessage;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.keys.token.CryptoTokenOfflineException;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.roles.Role;
import org.cesecore.roles.management.RoleSessionRemote;
import org.cesecore.roles.member.RoleMember;
import org.cesecore.roles.member.RoleMemberSessionRemote;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.config.WebConfiguration;
import org.ejbca.core.ejb.ca.sign.SignSessionRemote;
import org.ejbca.core.ejb.config.ConfigurationSessionRemote;
import org.ejbca.core.ejb.ra.CouldNotRemoveEndEntityException;
import org.ejbca.core.ejb.ra.EndEntityExistsException;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.core.ejb.ra.NoSuchEndEntityException;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.ca.AuthLoginException;
import org.ejbca.core.model.ca.AuthStatusException;
import org.ejbca.core.model.ra.CustomFieldException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileValidationException;
import org.jboss.resteasy.client.ClientExecutor;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.core.executors.ApacheHttpClient4Executor;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

/**
 * An intermediate class to support REST API system tests and setup the SSL connection/authentication.
 *
 * @version $Id: RestResourceSystemTestBase.java 29137 2018-06-07 12:40:12Z andrey_s_helmes $
 */
public class RestResourceSystemTestBase {

    private static final Logger log = Logger.getLogger(RestResourceSystemTestBase.class);
    // Shared EJB instances
    protected static final CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
    protected static final EndEntityManagementSessionRemote endEntityManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityManagementSessionRemote.class);
    protected static final InternalCertificateStoreSessionRemote internalCertificateStoreSession = EjbRemoteHelper.INSTANCE.getRemoteSession(InternalCertificateStoreSessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    protected static final RoleSessionRemote roleSession = EjbRemoteHelper.INSTANCE.getRemoteSession(RoleSessionRemote.class);
    protected static final RoleMemberSessionRemote roleMemberSession = EjbRemoteHelper.INSTANCE.getRemoteSession(RoleMemberSessionRemote.class);
    protected static final SignSessionRemote signSession = EjbRemoteHelper.INSTANCE.getRemoteSession(SignSessionRemote.class);
    //
    private static final ConfigurationSessionRemote CONFIGURATION_SESSION = EjbRemoteHelper.INSTANCE.getRemoteSession(ConfigurationSessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    //
    private static final String HTTPS_HOST = SystemTestsConfiguration.getRemoteHost(CONFIGURATION_SESSION.getProperty(WebConfiguration.CONFIG_HTTPSSERVERHOSTNAME));
    private static final String HTTPS_PORT = SystemTestsConfiguration.getRemotePortHttps(CONFIGURATION_SESSION.getProperty(WebConfiguration.CONFIG_HTTPSSERVERPRIVHTTPS));
    private static final HttpClient HTTP_CLIENT;
    private static ClientExecutor clientExecutor;
    //
    private static final String KEY_STORE_PASSWORD = "changeit";
    private static final String TRUSTED_STORE_PATH = System.getProperty("java.io.tmpdir") + File.separator + "truststore_" + new Date().getTime() + ".jks";
    private static final String TRUSTED_CA_NAME = "ManagementCA";
    private static final String CERTIFICATE_USER_NAME = "RestApiTestUser";
    private static final String CERTIFICATE_SUBJECT_DN = "CN=" + CERTIFICATE_USER_NAME;
    private static final String CERTIFICATE_PASSWORD = "RestApiTestUser123";
    private static final X509Certificate X_509_CERTIFICATE;
    private static final String LOGIN_STORE_PATH = System.getProperty("java.io.tmpdir") + File.separator + "restapitestuser_" + new Date().getTime() + ".jks";
    private static final String SUPER_ADMINISTRATOR_ROLE_NAME = "Super Administrator Role";
    private static final RoleMember ROLE_MEMBER;
    //
    private static final AuthenticationToken INTERNAL_ADMIN_TOKEN = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("EjbcaRestApiTest"));

    static {
        try {
            // Trusted CA setup: ManagementCA import into trustedKeyStore
            if(!caSession.existsCa(TRUSTED_CA_NAME)) {
                throw new RuntimeException("Cannot find the required CA [" + TRUSTED_CA_NAME + "]");
            }
            final CAInfo trustedCaInfo = caSession.getCAInfo(INTERNAL_ADMIN_TOKEN, TRUSTED_CA_NAME);
            final List<Certificate> trustedCaCertificateChain = trustedCaInfo.getCertificateChain();
            final KeyStore trustedKeyStore = initJksKeyStore(TRUSTED_STORE_PATH);
            importDataIntoJksKeystore(TRUSTED_STORE_PATH, trustedKeyStore, TRUSTED_CA_NAME.toLowerCase(), trustedCaCertificateChain.get(0).getEncoded(), null, null);
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustedKeyStore);
            // Login Certificate setup:
            // - ManagementCA import into loginKeyStore
            // - Sign a certificate by ManagementCA
            // - RestApiTestUser certificate and private key import into loginKeyStore
            final KeyStore loginKeyStore = initJksKeyStore(LOGIN_STORE_PATH);
            final EndEntityInformation endEntityInformation = createEndEntityInformation(trustedCaInfo.getCAId());
            final KeyPair keyPair = KeyTools.genKeys("2048", AlgorithmConstants.KEYALGORITHM_RSA);
            endEntityManagementSession.addUser(INTERNAL_ADMIN_TOKEN, endEntityInformation, false);
            final SimpleRequestMessage simpleRequestMessage = new SimpleRequestMessage(keyPair.getPublic(), endEntityInformation.getUsername(), endEntityInformation.getPassword());
            final X509ResponseMessage x509ResponseMessage = (X509ResponseMessage) signSession.createCertificate(INTERNAL_ADMIN_TOKEN, simpleRequestMessage, X509ResponseMessage.class, endEntityInformation);
            X_509_CERTIFICATE = (X509Certificate) x509ResponseMessage.getCertificate();
            importDataIntoJksKeystore(LOGIN_STORE_PATH, loginKeyStore, CERTIFICATE_USER_NAME.toLowerCase(), trustedCaCertificateChain.get(0).getEncoded(), keyPair, X_509_CERTIFICATE.getEncoded());
            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(loginKeyStore, KEY_STORE_PASSWORD.toCharArray());
            final Role role = roleSession.getRole(INTERNAL_ADMIN_TOKEN, null, SUPER_ADMINISTRATOR_ROLE_NAME);
            ROLE_MEMBER = roleMemberSession.persist(INTERNAL_ADMIN_TOKEN,
                    new RoleMember(
                            X509CertificateAuthenticationTokenMetaData.TOKEN_TYPE,
                            trustedCaInfo.getCAId(),
                            X500PrincipalAccessMatchValue.WITH_COMMONNAME.getNumericValue(),
                            AccessMatchType.TYPE_EQUALCASE.getNumericValue(),
                            CERTIFICATE_USER_NAME,
                            role.getRoleId(),
                            CERTIFICATE_USER_NAME + " for REST API Tests"
                    )
            );
            // Setup the SSL Context using prepared trustedKeyStore and loginKeyStore
            final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            HTTP_CLIENT = HttpClients.custom()
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
                    .build();
        } catch (AuthorizationDeniedException | CertificateException | NoSuchAlgorithmException | IOException |
                KeyStoreException | UnrecoverableKeyException | KeyManagementException | InvalidAlgorithmParameterException |
                CertificateSerialNumberException | EndEntityExistsException | CADoesntExistsException |
                WaitingForApprovalException | EndEntityProfileValidationException | ApprovalException |
                IllegalNameException | CustomFieldException | InvalidAlgorithmException | CustomCertificateSerialNumberException |
                AuthLoginException | NoSuchEndEntityException | CertificateExtensionException | CertificateRevokeException |
                IllegalValidityException | AuthStatusException | CryptoTokenOfflineException | SignRequestSignatureException |
                CertificateCreateException | SignRequestException | IllegalKeyException | CAOfflineException e) {
            // Remove any artifacts from the database
            clearLoginCertificateSetup();
            throw new RuntimeException("Cannot setup a HttpClient with SSL connection.", e);
        }
    }

    public static void beforeClass() {
    }

    public static void afterClass() throws Exception {
        clearLoginCertificateSetup();
        // Remove keystores
        final File trustedStore = new File(TRUSTED_STORE_PATH);
        if(trustedStore.exists()) {
            trustedStore.delete();
        }
        final File loginStore = new File(LOGIN_STORE_PATH);
        if(loginStore.exists()) {
            loginStore.delete();
        }
        // Close connections
        if(clientExecutor != null) {
            clientExecutor.close();
        }
    }

    /**
     * Forms a REST API request denoted by URI.
     * <br/>
     * For example newRequest("/v1/ca") forms the request on URL "https://localhost:8443/ejbca/ejbca-rest-api/v1/ca".
     *
     * @param uriPath a part of URL to make request on.
     *
     * @return An instance of ClientRequest.
     *
     * @see org.jboss.resteasy.client.ClientRequest
     */
    ClientRequest newRequest(final String uriPath) {
        clientExecutor = new ApacheHttpClient4Executor(HTTP_CLIENT);
        return new ClientRequest(getBaseUrl() + uriPath, clientExecutor);
    }

    private static String getBaseUrl() {
        return "https://"+ HTTPS_HOST +":" + HTTPS_PORT + "/ejbca/ejbca-rest-api";
    }

    private static KeyStore initJksKeyStore(final String keyStoreFilePath) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        final File file = new File(keyStoreFilePath);
        final KeyStore keyStore = KeyStore.getInstance("JKS");
        if (file.exists()) {
            keyStore.load(new FileInputStream(file), KEY_STORE_PASSWORD.toCharArray());
        } else {
            keyStore.load(null, null);
            keyStore.store(new FileOutputStream(file), KEY_STORE_PASSWORD.toCharArray());
        }
        return keyStore;
    }

    private static void importDataIntoJksKeystore(
            final String keyStoreFilePath,
            final KeyStore keyStore,
            final String keyStoreAlias,
            final byte[] issuerCertificateBytes,
            final KeyPair keyPair,
            final byte[] certificateBytes
    ) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
        // Add the certificate
        keyStore.setCertificateEntry(keyStoreAlias, getCertificateFromBytes(issuerCertificateBytes));
        // Add the key if exists
        if(keyPair != null) {
            final Certificate[] chain = { getCertificateFromBytes(certificateBytes) };
            keyStore.setKeyEntry(keyStoreAlias, keyPair.getPrivate(), KEY_STORE_PASSWORD.toCharArray(), chain);
        }
        // Save the new keystore contents
        final FileOutputStream fileOutputStream = new FileOutputStream(keyStoreFilePath);
        keyStore.store(fileOutputStream, KEY_STORE_PASSWORD.toCharArray());
        fileOutputStream.close();
    }

    private static Certificate getCertificateFromBytes(final byte[] certificateBytes) throws CertificateException {
        final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        final InputStream certificateInputStream = new ByteArrayInputStream(certificateBytes);
        return certificateFactory.generateCertificate(certificateInputStream);
    }

    private static EndEntityInformation createEndEntityInformation(final int caId) {
        final EndEntityInformation endEntityInformation = new EndEntityInformation(
                CERTIFICATE_USER_NAME,
                CERTIFICATE_SUBJECT_DN,
                caId,
                null,
                null,
                new EndEntityType(EndEntityTypes.ENDUSER),
                1,
                1,
                EndEntityConstants.TOKEN_SOFT_P12,
                0,
                null);
        endEntityInformation.setPassword(CERTIFICATE_PASSWORD);
        return endEntityInformation;
    }

    private static void clearLoginCertificateSetup() {
        // Remove the end entity if exists
        if (endEntityManagementSession.existsUser(CERTIFICATE_USER_NAME)) {
            try {
                endEntityManagementSession.deleteUser(INTERNAL_ADMIN_TOKEN, CERTIFICATE_USER_NAME);
            }
            catch (AuthorizationDeniedException | NoSuchEndEntityException | CouldNotRemoveEndEntityException e) {
                log.error("Cannot remove the user [" + CERTIFICATE_USER_NAME + "]", e);
            }
        }
        // Remove the certificate if exists
        if(X_509_CERTIFICATE != null) {
            internalCertificateStoreSession.removeCertificate(X_509_CERTIFICATE);
        }
        // Remove the membership
        if(ROLE_MEMBER != null) {
            try {
                roleMemberSession.remove(INTERNAL_ADMIN_TOKEN, ROLE_MEMBER.getId());
            }
            catch (AuthorizationDeniedException e) {
                log.error("Cannot remove the membership", e);
            }
        }
    }

}
