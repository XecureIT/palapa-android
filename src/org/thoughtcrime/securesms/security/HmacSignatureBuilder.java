package org.thoughtcrime.securesms.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import org.jsoup.helper.StringUtil;
import org.thoughtcrime.securesms.util.Base64;

public class HmacSignatureBuilder {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    //    private static final byte DELIMITER = '\n';
    private String algorithm = HMAC_SHA_256;
    private String userTime;
    private String userSalt;
    private String sharedSecret;

    public String getAlgorithm() {
        return HMAC_SHA_256;
    }

    public HmacSignatureBuilder algorithm(String algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    public HmacSignatureBuilder userTime(String userTime) {
        this.userTime = userTime;
        return this;
    }

    public HmacSignatureBuilder userSalt(String userSalt) {
        this.userSalt = userSalt;
        return this;
    }

    public HmacSignatureBuilder sharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
        return this;
    }

    public byte[] build() {
        Objects.requireNonNull(algorithm, "algorithm");
        // Objects.requireNonNull(userTime, "userTime");
        Objects.requireNonNull(userSalt, "userSalt");
        Objects.requireNonNull(sharedSecret, "sharedSecret");

        try {
            final Mac digest = Mac.getInstance(algorithm);
            SecretKeySpec secretKey = new SecretKeySpec(sharedSecret.getBytes(), algorithm);
            digest.init(secretKey);
            if (!StringUtil.isBlank(userTime)) {
                digest.update(userTime.getBytes());
            }
            digest.update(userSalt.getBytes());
            final byte[] signatureBytes = digest.doFinal();
            digest.reset();
            return signatureBytes;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Can't create signature: " + e.getMessage(), e);
        }
    }

    public boolean isHashEquals(byte[] expectedSignature) {
        final byte[] signature = build();
        return MessageDigest.isEqual(signature, expectedSignature);
    }

    public String buildAsBase64String() {
        return Base64.encodeBytes(build());
    }

}