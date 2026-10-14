package io.agenticawithakka.connectors.mock;

import io.agenticawithakka.application.ports.PortException;
import io.agenticawithakka.domain.contracts.ErrorCode;
import io.agenticawithakka.domain.contracts.IdentityContextRef;
import io.agenticawithakka.domain.contracts.ProjectId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-signed opaque credentials for the local mock source only. */
final class MockDelegationTokenCodec {
    private static final int MIN_KEY_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 16_384;
    private final byte[] signingKey;

    MockDelegationTokenCodec(byte[] signingKey) {
        if (signingKey == null || signingKey.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException("mock signing key must contain at least 32 bytes");
        }
        this.signingKey = signingKey.clone();
    }

    String issue(Claims claims) {
        String scopeValue = String.join(",", claims.scopes().stream().sorted().toList());
        String payload = String.join(
                "\n",
                encode(claims.subject()),
                encode(claims.projectId().value()),
                encode(claims.identityContextRef().value()),
                encode(claims.audience()),
                encode(Long.toString(claims.expiresAtEpochSecond())),
                encode(scopeValue));
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return "v2."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
                + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payloadBytes));
    }

    Claims verify(String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            throw denied();
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals("v2")) {
                throw denied();
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(sign(payload), signature)) {
                throw denied();
            }
            String[] fields = new String(payload, StandardCharsets.US_ASCII).split("\n", -1);
            if (fields.length != 6 || fields[5].isBlank()) {
                throw denied();
            }
            var scopes = Set.copyOf(Arrays.asList(decode(fields[5]).split(",", -1)));
            return new Claims(
                    decode(fields[0]),
                    new ProjectId(decode(fields[1])),
                    new IdentityContextRef(decode(fields[2])),
                    decode(fields[3]),
                    Long.parseLong(decode(fields[4])),
                    scopes);
        } catch (IllegalArgumentException e) {
            throw denied();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static PortException denied() {
        return new PortException(ErrorCode.DENIED, "mock delegated credential is invalid");
    }

    record Claims(
            String subject,
            ProjectId projectId,
            IdentityContextRef identityContextRef,
            String audience,
            long expiresAtEpochSecond,
            Set<String> scopes) {}
}
