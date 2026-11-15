package org.knowledgeroot.test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal OpenID Connect provider for tests (R64): discovery, authorization code flow, RS256
 * ID tokens, JWKS and userinfo. The test says which user the next sign-in returns; the authorize
 * endpoint redirects straight back with a code, as a provider does after a successful login.
 */
public final class FakeOidcProvider implements AutoCloseable {
    private final HttpServer server;
    private final RSAKey key;
    private final String clientId;
    private final Map<String, Map<String, Object>> codes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> accessTokens = new ConcurrentHashMap<>();
    private volatile Map<String, Object> nextClaims = Map.of("sub", "nobody");
    private volatile Map<String, String> lastAuthorization = Map.of();

    public FakeOidcProvider(String clientId) throws Exception {
        this.clientId = clientId;
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).keyID("test-key").build();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/.well-known/openid-configuration", exchange -> json(exchange, 200, discovery()));
        server.createContext("/authorize", this::authorize);
        server.createContext("/token", this::token);
        server.createContext("/jwks", exchange -> json(exchange, 200, new JWKSet(key.toPublicJWK()).toString()));
        server.createContext("/userinfo", this::userinfo);
        server.start();
    }

    public String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Claims of the user the next sign-in returns: {@code sub} plus standard claims and anything else (for example groups). */
    public void nextUser(Map<String, Object> claims) {
        nextClaims = new LinkedHashMap<>(claims);
    }

    /** The parameters of the last authorization request, for assertions on scope, PKCE and redirect URI. */
    public Map<String, String> lastAuthorization() {
        return lastAuthorization;
    }

    /** Visits an authorization URL the way a browser would and returns the redirect back to the application. */
    public URI follow(URI authorizationUrl) throws Exception {
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            var response = client.send(HttpRequest.newBuilder(authorizationUrl).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 302) throw new IllegalStateException("authorize answered " + response.statusCode() + ": " + response.body());
            return URI.create(response.headers().firstValue("Location").orElseThrow());
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private String discovery() {
        return JSONObjectUtils.toJSONString(Map.ofEntries(
                Map.entry("issuer", issuer()),
                Map.entry("authorization_endpoint", issuer() + "/authorize"),
                Map.entry("token_endpoint", issuer() + "/token"),
                Map.entry("jwks_uri", issuer() + "/jwks"),
                Map.entry("userinfo_endpoint", issuer() + "/userinfo"),
                Map.entry("response_types_supported", List.of("code")),
                Map.entry("subject_types_supported", List.of("public")),
                Map.entry("id_token_signing_alg_values_supported", List.of("RS256")),
                Map.entry("scopes_supported", List.of("openid", "profile", "email")),
                Map.entry("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post", "none")),
                Map.entry("code_challenge_methods_supported", List.of("S256"))));
    }

    private void authorize(HttpExchange exchange) throws IOException {
        var params = parse(exchange.getRequestURI().getRawQuery());
        lastAuthorization = params;
        if (!clientId.equals(params.get("client_id"))) { json(exchange, 400, "{\"error\":\"unauthorized_client\"}"); return; }
        if (!"code".equals(params.get("response_type")) || params.get("redirect_uri") == null) { json(exchange, 400, "{\"error\":\"invalid_request\"}"); return; }
        String code = UUID.randomUUID().toString();
        var claims = new LinkedHashMap<>(nextClaims);
        if (params.get("nonce") != null) claims.put("nonce", params.get("nonce"));
        codes.put(code, claims);
        String location = params.get("redirect_uri") + "?code=" + code + (params.get("state") == null ? "" : "&state=" + encode(params.get("state")));
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void token(HttpExchange exchange) throws IOException {
        var form = parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (!"authorization_code".equals(form.get("grant_type"))) { json(exchange, 400, "{\"error\":\"unsupported_grant_type\"}"); return; }
        var claims = codes.remove(form.get("code"));
        if (claims == null) { json(exchange, 400, "{\"error\":\"invalid_grant\"}"); return; }
        try {
            var builder = new JWTClaimsSet.Builder().issuer(issuer()).audience(clientId)
                    .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 300_000));
            for (var entry : claims.entrySet()) builder.claim(entry.getKey(), entry.getValue());
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), builder.build());
            jwt.sign(new RSASSASigner(key));
            String accessToken = UUID.randomUUID().toString();
            accessTokens.put(accessToken, claims);
            json(exchange, 200, JSONObjectUtils.toJSONString(Map.of(
                    "access_token", accessToken, "token_type", "Bearer", "expires_in", 300,
                    "scope", "openid profile email", "id_token", jwt.serialize())));
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    private void userinfo(HttpExchange exchange) throws IOException {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        var claims = header != null && header.startsWith("Bearer ") ? accessTokens.get(header.substring(7)) : null;
        if (claims == null) { json(exchange, 401, "{\"error\":\"invalid_token\"}"); return; }
        var body = new HashMap<>(claims);
        body.remove("nonce");
        json(exchange, 200, JSONObjectUtils.toJSONString(body));
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Map<String, String> parse(String query) {
        var result = new LinkedHashMap<String, String>();
        if (query == null || query.isEmpty()) return result;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(name, value);
        }
        return result;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
