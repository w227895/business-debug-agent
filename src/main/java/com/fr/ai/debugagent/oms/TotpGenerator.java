package com.fr.ai.debugagent.oms;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;

@Component
public class TotpGenerator {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;

    public String now(String base32Secret) {
        return generate(base32Secret, Instant.now(), DIGITS);
    }

    String generate(String base32Secret, Instant instant, int digits) {
        if (!StringUtils.hasText(base32Secret)) {
            throw new IllegalArgumentException("TOTP secret is required");
        }
        byte[] key = decodeBase32(base32Secret);
        long counter = instant.getEpochSecond() / TIME_STEP_SECONDS;
        byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
        byte[] hash = hmacSha1(key, counterBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int modulo = (int) Math.pow(10, digits);
        return String.format(Locale.ROOT, "%0" + digits + "d", binary % modulo);
    }

    private byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate TOTP code", ex);
        }
    }

    private byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        byte[] result = new byte[normalized.length() * 5 / 8];
        int index = 0;
        for (char item : normalized.toCharArray()) {
            int current = decodeBase32Char(item);
            buffer = (buffer << 5) | current;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        if (index == result.length) {
            return result;
        }
        byte[] exact = new byte[index];
        System.arraycopy(result, 0, exact, 0, index);
        return exact;
    }

    private int decodeBase32Char(char item) {
        if (item >= 'A' && item <= 'Z') {
            return item - 'A';
        }
        if (item >= '2' && item <= '7') {
            return item - '2' + 26;
        }
        throw new IllegalArgumentException("Invalid Base32 character in TOTP secret");
    }
}
