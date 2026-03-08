package com.afrochow.security.Utils;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public final class JwtUtil {

    public static String buildToken(Map<String, Object> claims,
                                    String subject,
                                    Date issuedAt,
                                    Date expiration,
                                    SecretKey signingKey) {
        return Jwts.builder()
                .claims(claims != null ? claims : Map.of())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    public static Claims parseToken(String token, SecretKey signingKey) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    /* --------- AES-GCM --------- */
    private static final int GCM_IV_LEN  = 12;
    private static final int GCM_TAG_LEN = 128;
    private static final SecureRandom RAND = new SecureRandom();

    public static String encrypt(String plaintext, SecretKey aesKey) {
        try {
            byte[] iv = new byte[GCM_IV_LEN];
            RAND.nextBytes(iv);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LEN, iv));

            byte[] cipherText = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(buf.array());
        } catch (Exception e) {
            throw new SecurityException("Encryption failed", e);
        }
    }

    public static String decrypt(String encoded, SecretKey aesKey) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            ByteBuffer buf   = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LEN];
            buf.get(iv);

            byte[] cipherText = new byte[buf.remaining()];
            buf.get(cipherText);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LEN, iv));

            return new String(c.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("Decryption failed", e);
        }
    }

    /* --------- helpers --------- */
    public static SecretKey hs512KeyFrom(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static SecretKey aes256KeyFrom(byte[] keyBytes) {
        if (keyBytes.length != 32) throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        return new SecretKeySpec(keyBytes, "AES");
    }

    private JwtUtil() {}
}