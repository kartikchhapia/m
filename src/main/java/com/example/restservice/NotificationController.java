package com.example.restservice;

import com.microsoft.graph.requests.SubscriptionCollectionPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.microsoft.graph.serializer.DefaultSerializer;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.logger.DefaultLogger;

import org.springframework.http.*;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.time.OffsetDateTime;

import org.apache.commons.codec.binary.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import io.jsonwebtoken.*;
import okhttp3.Request;

import org.apache.commons.text.StringEscapeUtils;

@RestController
public class NotificationController {
    // region app registration information
    private final String clientId = "73980781-82f1-40fb-9529-4dac18dff6b6";
    private final String clientSecret = "6n6.qQc~fP-2l68hD55Wzw4BAFeR5-du4_";
    private final String tenantId = "11c462fd-078f-473d-8339-a96968bd744e";
    // endregion
    // region subscription information
    private final String publicUrl = "https://d4216c0714ef.ngrok.io"; // eg https://c2ddde53.ngrok.io no trailing slash
    private final String resource = "/communications/callRecords"; // eg
    // teams/9c05f27f-f866-4cc0-b4c2-6225a4568bc5/channels/19:015c392a3030451f8b52fac6084be56d@thread.skype/messages
    private final String changeType = "created";
    // endregion
    // region certificate information
    private final String storename = "JKSkeystore.jks";
    private final String alias = "selfsignedjks";
    private final String storepass = "sapna_123";
    // endregion

    private final List<String> scopes = Arrays.asList("https://graph.microsoft.com/.default");
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    private JwkKeyResolver jwksResolver;

    @GetMapping("/notification")
    @ResponseBody
    public String subscribe() throws KeyStoreException, FileNotFoundException, IOException, CertificateException,
            NoSuchAlgorithmException, InterruptedException, ExecutionException {

        System.out.println("inside create subscrition");
        return createSubscription()
                        .thenApply(s -> getMessageToDisplay(s))
                        .get();
    }
    private CompletableFuture<Subscription> createSubscription() {
        final GraphServiceClient<Request> graphClient = getClient();
        graphClient.setServiceRoot("https://graph.microsoft.com/beta");
        final Subscription subscription = new Subscription();
        subscription.changeType = this.changeType;
        subscription.notificationUrl = this.publicUrl + "/notification";
        subscription.resource = this.resource;
        subscription.expirationDateTime = OffsetDateTime.now().plusHours(1L);
        subscription.clientState = "secretClientValue";

        if (this.resource.startsWith("teams")) {
            subscription.includeResourceData = true;
            subscription.encryptionCertificate = GetBase64EncodedCertificate();
            subscription.encryptionCertificateId = this.alias;
            LOGGER.warn("encoded cert");
            LOGGER.info(GetBase64EncodedCertificate());
        }

        return graphClient.subscriptions().buildRequest().postAsync(subscription);

        /*


         */
    }

    @GetMapping("/delete")
    @ResponseBody
    public String delete() throws KeyStoreException, FileNotFoundException, IOException, CertificateException,
            NoSuchAlgorithmException, InterruptedException, ExecutionException {

        final ClientSecretCredential defaultCredential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build();

        final IAuthenticationProvider authProvider = new TokenCredentialAuthProvider(this.scopes, defaultCredential);

        GraphServiceClient graphClient = GraphServiceClient.builder().authenticationProvider( authProvider ).buildClient();

        graphClient.subscriptions("fb1b405d-3ed2-4a34-a342-19c2c910ba1c").buildRequest().delete();


        //graphClient.subscriptions().buildRequest().postAsync(subscriptions);

        /*
        GraphServiceClient graphClient = GraphServiceClient.builder().authenticationProvider( authProvider ).buildClient();

        graphClient.subscriptions("7f105c7d-2dc5-4530-97cd-4e7ae6534c07")
                .buildRequest()
                .delete();
                */

        return null;

    }

    @SuppressWarnings("unchecked")
    private GraphServiceClient<Request> getClient() {
        final ClientSecretCredential defaultCredential = new ClientSecretCredentialBuilder()
                                                    .clientId(clientId)
                                                    .clientSecret(clientSecret)
                                                    .tenantId(tenantId)
                                                    .build();
        final IAuthenticationProvider authProvider = new TokenCredentialAuthProvider(this.scopes, defaultCredential);
        return GraphServiceClient.builder().authenticationProvider(authProvider).buildClient();
    }
    private String getMessageToDisplay(Subscription subscription) {
        return "Subscribed to entity with subscription id " + subscription.id;
    }

    private KeyStore GetCertificateStore() throws KeyStoreException, FileNotFoundException, IOException,
            CertificateException, NoSuchAlgorithmException {
        final KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(this.storename), this.storepass.toCharArray());
        return ks;
    }

    private String GetBase64EncodedCertificate() {
        try {
            final KeyStore ks = this.GetCertificateStore();
            final java.security.cert.Certificate cert = ks.getCertificate(this.alias);
            return new String(Base64.encodeBase64(cert.getEncoded()));
        }
        catch (Exception ex) {
            this.LOGGER.error("Error getting the certificate", ex);
            return null;
        }
    }

    private byte[] GetEncryptionKey(final String base64encodedSymetricKey) throws KeyStoreException,
            NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, FileNotFoundException,
            NoSuchPaddingException, IOException, UnrecoverableKeyException, BadPaddingException, CertificateException {
        final KeyStore ks = this.GetCertificateStore();
        final Key asymmetricKey = ks.getKey(this.alias, this.storepass.toCharArray());
        final byte[] encryptedSymetricKey = Base64.decodeBase64(base64encodedSymetricKey);
        final Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, asymmetricKey);
        return cipher.doFinal(encryptedSymetricKey);
    }

    private boolean IsDataSignatureValid(final byte[] encryptionKey, final String encryptedData,
            final String comparisonSignature) throws NoSuchAlgorithmException, InvalidKeyException {
        final byte[] decodedEncryptedData = Base64.decodeBase64(encryptedData);
        final Mac mac = Mac.getInstance("HMACSHA256");
        final SecretKey skey = new SecretKeySpec(encryptionKey, "HMACSHA256");
        mac.init(skey);
        final byte[] hashedData = mac.doFinal(decodedEncryptedData);
        final String encodedHashedData = new String(Base64.encodeBase64(hashedData));
        return comparisonSignature.equals(encodedHashedData);
    }

    private String GetDecryptedData(final byte[] encryptionKey, final String encryptedData)
            throws NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, NoSuchPaddingException,
            BadPaddingException, InvalidAlgorithmParameterException {
        final SecretKey skey = new SecretKeySpec(encryptionKey, "AES");
        final IvParameterSpec ivspec = new IvParameterSpec(Arrays.copyOf(encryptionKey, 16));
        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");// should be 7 but
                                                                         // https://stackoverflow.com/a/53139355/3808675
        cipher.init(Cipher.DECRYPT_MODE, skey, ivspec);
        return new String(cipher.doFinal(Base64.decodeBase64(encryptedData)));
    }

    private boolean IsValidationTokenValid(final String[] validAudiences, final String[] validTenantIds,
            final String serializedToken) {
        try {
            if (this.jwksResolver == null) {
                this.jwksResolver = new JwkKeyResolver();
            }
            final Jws<Claims> token = Jwts.parserBuilder().setSigningKeyResolver(this.jwksResolver).build()
                    .parseClaimsJws(serializedToken);
            final Claims body = token.getBody();
            final String audience = body.getAudience();
            boolean isAudienceValid = false;
            for (final String validAudience : validAudiences) {
                isAudienceValid = isAudienceValid || validAudience.equals(audience);
            }
            final String issuer = body.getIssuer();
            boolean isTenantValid = false;
            for (final String validTenantId : validTenantIds) {
                isTenantValid = isTenantValid || issuer.endsWith(validTenantId + "/");
            }
            return isAudienceValid && isTenantValid; // nbf,exp and signature are already validated by library
        } catch (final Exception e) {
            LOGGER.error("could not validate token");
            LOGGER.error(e.getMessage());
            return false;
        }
    }

    @PostMapping(value = "/notification", headers = { "content-type=text/plain" })
    @ResponseBody
    public ResponseEntity<String> handleValidation(
            @RequestParam(value = "validationToken") final String validationToken) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                .body(StringEscapeUtils.escapeHtml4(validationToken));
    }

    @PostMapping("/notification")
    @ResponseBody
    public ResponseEntity<String> handleNotification(@RequestBody() final String jsonString)
            throws KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException,
            FileNotFoundException, NoSuchPaddingException, IOException, UnrecoverableKeyException, BadPaddingException,
            CertificateException, InvalidAlgorithmParameterException {
        final DefaultSerializer defaultSerializer = new DefaultSerializer(new DefaultLogger());
        final ChangeNotificationCollection notifications = defaultSerializer.deserializeObject(jsonString, ChangeNotificationCollection.class);
        LOGGER.info("notification received, "+jsonString);
        LOGGER.info(notifications.value.get(0).resource);
        LOGGER.info(notifications.value.get(0).resource);

        final byte[] decryptedKey = this.GetEncryptionKey(notifications.value.get(0).encryptedContent.dataKey);
        final boolean isDataSignatureValid = this.IsDataSignatureValid(decryptedKey,
                notifications.value.get(0).encryptedContent.data,
                notifications.value.get(0).encryptedContent.dataSignature);
        boolean areValidationTokensValid = true;
        for (final String validationToken : notifications.validationTokens) {
            areValidationTokensValid = areValidationTokensValid && this.IsValidationTokenValid(
                    new String[] { this.clientId }, new String[] { this.tenantId }, validationToken);
        }
        if (areValidationTokensValid && isDataSignatureValid) {
            final String decryptedData = this.GetDecryptedData(decryptedKey,
                    notifications.value.get(0).encryptedContent.data);
            LOGGER.info("decrypted data");
            LOGGER.info(decryptedData);
            return ResponseEntity.ok().body("");
        } else if(!isDataSignatureValid) {
            return ResponseEntity.badRequest().body("data signature validation failed");
        } else {
            return ResponseEntity.badRequest().body("token validation failed");
        }
	}
}
