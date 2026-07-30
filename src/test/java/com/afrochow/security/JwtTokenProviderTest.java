package com.afrochow.security;

import com.afrochow.common.enums.AuthProvider;
import com.afrochow.common.enums.Role;
import com.afrochow.user.model.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real crypto path (HS512 signing + AES-256-GCM encryption) end to
 * end — no mocking, since the whole point of this class is "does a token actually
 * round-trip correctly and get rejected when it shouldn't."
 */
class JwtTokenProviderTest {

    // >= 64 chars, no "secret"/"change-me"/"placeholder" substrings — see
    // JwtTokenProvider.validateJwtSecret().
    private static final String VALID_SECRET =
            "af-Q7mK2pL9vX4nR8tY1wZ6cH3jD5gS0bN7fA2kM4rP9xV6yT1uW8eC3iO5lJ0qz";

    private JwtTokenProvider provider;
    private User user;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", VALID_SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 900_000L); // 15 min
        ReflectionTestUtils.setField(provider, "encryptionEnabled", true);
        byte[] aesKeyBytes = new byte[32];
        new SecureRandom().nextBytes(aesKeyBytes);
        ReflectionTestUtils.setField(provider, "encryptionKeyBase64", Base64.getEncoder().encodeToString(aesKeyBytes));
        provider.init();

        user = User.builder()
                .userId(1L).publicUserId("CUS123").username("adecustomer")
                .email("customer@example.com").role(Role.CUSTOMER)
                .authProvider(AuthProvider.EMAIL).build();
    }

    @Test
    void createToken_thenReadItBack_roundTripsAllClaims() {
        String token = provider.createToken(user);

        assertThat(provider.extractUsername(token)).isEqualTo("adecustomer");
        assertThat(provider.extractEmail(token)).isEqualTo("customer@example.com");
        assertThat(provider.extractPublicUserId(token)).isEqualTo("CUS123");
        assertThat(provider.extractRole(token)).isEqualTo("CUSTOMER");
    }

    @Test
    void createToken_withEncryptionEnabled_producesOpaqueCiphertextNotRawJwt() {
        String token = provider.createToken(user);

        // A raw (unencrypted) JWT is always three base64url segments joined by dots
        // (header.payload.signature). The encrypted form is a single AES-GCM blob —
        // asserting there's no dot proves encryption actually ran, not just that
        // *a* string came back.
        assertThat(token).doesNotContain(".");
    }

    @Test
    void createToken_withEncryptionDisabled_producesRawThreePartJwt() {
        ReflectionTestUtils.setField(provider, "encryptionEnabled", false);

        String token = provider.createToken(user);

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(provider.extractUsername(token)).isEqualTo("adecustomer");
    }

    @Test
    void isValidToken_forFreshlyIssuedToken_returnsTrue() {
        String token = provider.createToken(user);
        assertThat(provider.isValidToken(token)).isTrue();
    }

    // NOTE: JwtUtil.decrypt() runs OUTSIDE readToken()'s try/catch and wraps every
    // failure (bad base64, bad GCM tag) in a plain SecurityException — which
    // isValidToken()'s catch clause (JwtException | IllegalArgumentException)
    // does NOT catch. So with encryption enabled, garbage/tampered ciphertext makes
    // isValidToken() throw rather than return false. In production this is masked
    // by JwtAuthenticationFilter's outer catch-all (falls back to unauthenticated),
    // but isValidToken() itself doesn't honor its own "returns false on invalid
    // token" contract for this case — documenting the real behavior here rather
    // than the intended one.
    @Test
    void isValidToken_forGarbageString_throwsRatherThanReturningFalse() {
        assertThatThrownBy(() -> provider.isValidToken("not-a-real-token"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void isValidToken_forTamperedCiphertext_throwsRatherThanReturningFalse() {
        String token = provider.createToken(user);
        // Flip one character in the middle of the encrypted blob so the GCM auth
        // tag no longer matches.
        char[] chars = token.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'a' ? 'b' : 'a';
        String tampered = new String(chars);

        assertThatThrownBy(() -> provider.isValidToken(tampered))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void isValidToken_forExpiredToken_returnsFalse() {
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", -60_000L); // already expired on issue
        String expiredToken = provider.createToken(user);

        assertThat(provider.isValidToken(expiredToken)).isFalse();
    }

    @Test
    void readToken_forExpiredToken_throwsJwtException() {
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", -60_000L);
        String expiredToken = provider.createToken(user);

        assertThatThrownBy(() -> provider.readToken(expiredToken))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void isTokenExpired_forFreshToken_returnsFalse() {
        String token = provider.createToken(user);
        assertThat(provider.isTokenExpired(token)).isFalse();
    }

    @Test
    void extractUserInfo_returnsAllFieldsPopulated() {
        String token = provider.createToken(user);

        JwtTokenProvider.UserInfo info = provider.extractUserInfo(token);

        assertThat(info.username()).isEqualTo("adecustomer");
        assertThat(info.email()).isEqualTo("customer@example.com");
        assertThat(info.publicUserId()).isEqualTo("CUS123");
        assertThat(info.role()).isEqualTo("CUSTOMER");
        assertThat(info.issuedAt()).isNotNull();
        assertThat(info.expiration()).isAfter(info.issuedAt());
    }

    @Test
    void createToken_incompleteUser_throwsIllegalArgument() {
        User incomplete = User.builder().username("adecustomer").role(Role.CUSTOMER).build(); // missing email/publicUserId

        assertThatThrownBy(() -> provider.createToken(incomplete))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getAccessTokenExpirationSeconds_convertsMillisToSeconds() {
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 900_000L);
        assertThat(provider.getAccessTokenExpirationSeconds()).isEqualTo(900L);
    }

    @Test
    void init_rejectsShortSecret() {
        JwtTokenProvider shortSecretProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortSecretProvider, "jwtSecret", "too-short");
        ReflectionTestUtils.setField(shortSecretProvider, "jwtExpirationMs", 900_000L);
        ReflectionTestUtils.setField(shortSecretProvider, "encryptionEnabled", false);

        assertThatThrownBy(shortSecretProvider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64");
    }

    @Test
    void init_rejectsPlaceholderSecret() {
        JwtTokenProvider placeholderProvider = new JwtTokenProvider();
        // 64+ chars but contains a banned placeholder substring
        ReflectionTestUtils.setField(placeholderProvider, "jwtSecret",
                "change-me-change-me-change-me-change-me-change-me-change-me-please");
        ReflectionTestUtils.setField(placeholderProvider, "jwtExpirationMs", 900_000L);
        ReflectionTestUtils.setField(placeholderProvider, "encryptionEnabled", false);

        assertThatThrownBy(placeholderProvider::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }
}
