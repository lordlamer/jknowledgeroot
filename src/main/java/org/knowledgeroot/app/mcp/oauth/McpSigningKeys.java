package org.knowledgeroot.app.mcp.oauth;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.mfa.SecretCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * The RSA keys that sign MCP access tokens, kept in {@code oauth2_signing_key} so that every
 * instance signs and verifies with the same keys across restarts. The private key is stored
 * encrypted with the master key. The newest key signs; all keys verify, which leaves room for a
 * rotation by inserting a new row.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpSigningKeys implements JWKSource<SecurityContext> {
    private static final Duration RELOAD = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private volatile JWKSet keys;
    private volatile Instant loadedAt = Instant.EPOCH;

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        List<JWK> matches = selector.select(jwkSet());
        // The encoder asks without a key id and must get exactly one key: the newest.
        if (matches.size() > 1 && (selector.getMatcher().getKeyIDs() == null || selector.getMatcher().getKeyIDs().isEmpty()))
            return List.of(matches.getFirst());
        return matches;
    }

    private JWKSet jwkSet() {
        JWKSet current = keys;
        if (current != null && loadedAt.plus(RELOAD).isAfter(Instant.now())) return current;
        synchronized (this) {
            if (keys != null && loadedAt.plus(RELOAD).isAfter(Instant.now())) return keys;
            List<JWK> loaded = load();
            if (loaded.isEmpty()) {
                generate();
                loaded = load();
            }
            keys = new JWKSet(loaded);
            loadedAt = Instant.now();
            return keys;
        }
    }

    private List<JWK> load() {
        return jdbc.query("SELECT id, private_key_enc, public_key FROM oauth2_signing_key ORDER BY created_at DESC, id",
                (rs, row) -> toKey(rs.getString("id"), rs.getBytes("private_key_enc"), rs.getString("public_key")));
    }

    private JWK toKey(String id, byte[] privateKeyEnc, String publicKey) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var publicSpec = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey));
            var privateSpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(cipher.decrypt(privateKeyEnc)));
            return new RSAKey.Builder((RSAPublicKey) factory.generatePublic(publicSpec))
                    .privateKey((RSAPrivateKey) factory.generatePrivate(privateSpec)).keyID(id).build();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Cannot read the MCP signing key " + id, e);
        }
    }

    private void generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO oauth2_signing_key (id, private_key_enc, public_key, created_at) VALUES (?, ?, ?, ?)", id,
                    cipher.encrypt(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())),
                    Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()), LocalDateTime.now());
            log.info("Generated MCP token signing key {}", id);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA unavailable", e);
        }
    }
}
