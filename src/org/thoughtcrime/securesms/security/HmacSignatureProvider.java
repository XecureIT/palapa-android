package org.thoughtcrime.securesms.security;

import java.security.SecureRandom;
import java.util.Date;

public class HmacSignatureProvider {

    private final String sharedSecret;
    private final String sharedSalt;

    public HmacSignatureProvider(String sharedSecret) {
        this(sharedSecret, "");
    }

    public HmacSignatureProvider(String sharedSecret, String sharedSalt) {
        this.sharedSecret = sharedSecret;
        this.sharedSalt = sharedSalt;
    }

    public String generateUserTime(){

        StringBuilder buffer = new StringBuilder(20);
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 20; i++) {
            int randomLimitedInt = 97 + (int)
                    (random.nextFloat() * (122 - 97 + 1));
            buffer.append((char) randomLimitedInt);
        }
        return String.valueOf(new Date().getTime())+":"+buffer.toString();

    }

    public String generateAuthToken(String username){
        final HmacSignatureBuilder signatureBuilder = new HmacSignatureBuilder()
                .sharedSecret(sharedSecret)
                .userSalt(username.concat(sharedSalt));

        return signatureBuilder.buildAsBase64String();
    }

    public String generateAuthToken(String username, String userTime){
        final HmacSignatureBuilder signatureBuilder = new HmacSignatureBuilder()
                .sharedSecret(sharedSecret)
                .userSalt(username.concat(sharedSalt))
                .userTime(userTime);

        return signatureBuilder.buildAsBase64String();
    }

}