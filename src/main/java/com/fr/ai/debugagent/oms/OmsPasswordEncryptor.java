package com.fr.ai.debugagent.oms;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Component
public class OmsPasswordEncryptor {

    public String encrypt(String rawPassword, String publicKeyHex) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("OMS password is required");
        }
        if (!StringUtils.hasText(publicKeyHex)) {
            throw new IllegalArgumentException("OMS SM2 public key is required");
        }
        String payload = md5(rawPassword) + "-" + System.currentTimeMillis();
        byte[] encrypted = sm2Encrypt(payload.getBytes(StandardCharsets.UTF_8), publicKeyHex);
        if (encrypted.length > 0 && encrypted[0] == 0x04) {
            byte[] withoutPrefix = new byte[encrypted.length - 1];
            System.arraycopy(encrypted, 1, withoutPrefix, 0, withoutPrefix.length);
            encrypted = withoutPrefix;
        }
        return Hex.toHexString(encrypted);
    }

    private byte[] sm2Encrypt(byte[] data, String publicKeyHex) {
        try {
            X9ECParameters parameters = GMNamedCurves.getByName("sm2p256v1");
            ECDomainParameters domainParameters = new ECDomainParameters(
                    parameters.getCurve(),
                    parameters.getG(),
                    parameters.getN(),
                    parameters.getH(),
                    parameters.getSeed());
            byte[] publicKeyBytes = Hex.decode(publicKeyHex);
            ECPoint publicPoint = parameters.getCurve().decodePoint(publicKeyBytes);
            ECPublicKeyParameters publicKey = new ECPublicKeyParameters(publicPoint, domainParameters);
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(true, new ParametersWithRandom(publicKey, new SecureRandom()));
            return engine.processBlock(data, 0, data.length);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt OMS password with SM2", ex);
        }
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Hex.toHexString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to MD5 OMS password", ex);
        }
    }
}
