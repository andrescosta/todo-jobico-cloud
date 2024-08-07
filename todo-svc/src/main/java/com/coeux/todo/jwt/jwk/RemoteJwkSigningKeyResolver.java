package com.coeux.todo.jwt.jwk;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SigningKeyResolver;
import io.jsonwebtoken.io.Decoders;

// With help of: https://github.com/okta/okta-jwt-verifier-java/blob/master/impl/src/main/java/com/okta/jwt/impl/jjwt/RemoteJwkSigningKeyResolver.java
public final class RemoteJwkSigningKeyResolver implements SigningKeyResolver {

    private final URI jwkUri;
    private final Object lock = new Object();
    private volatile Map<String, Key> keyMap = new HashMap<>();

    public RemoteJwkSigningKeyResolver(URI jwkUri) {
        this.jwkUri = jwkUri;
    }

    @Override
    public Key resolveSigningKey(JwsHeader header, Claims claims) {
        return getKey(header.getKeyId());
    }

    @Override
    public Key resolveSigningKey(JwsHeader header, String plaintext) {
        return getKey(header.getKeyId());
    }

    private Key getKey(String keyId) {

        Key result = keyMap.get(keyId);
        if (result != null) {
            return result;
        }

        synchronized (lock) {
            result = keyMap.get(keyId);
            if (result != null) {
                return result;
            }

            updateKeys();
            return keyMap.get(keyId);
        }
    }

    public void updateKeys() {
        try {

            var trustManager = new X509ExtendedTrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[] {};
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                }
            };
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustManager }, new SecureRandom());

            ObjectMapper objectMapper = new ObjectMapper();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(jwkUri)
                    .GET()
                    .build();
            var client = HttpClient.newBuilder().sslContext(sslContext).build();
            HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());
            Map<String, Key> newKeys = objectMapper.readValue(response.body(), JwkKeys.class).getKeys().stream()
                    .filter(jwkKey -> "sig".equals(jwkKey.getPublicKeyUse()))
                    .filter(jwkKey -> "RSA".equals(jwkKey.getKeyType()))
                    .collect(Collectors.toMap(JwkKey::getKeyId, jwkKey -> {
                        BigInteger modulus = base64ToBigInteger(jwkKey.getPublicKeyModulus());
                        BigInteger exponent = base64ToBigInteger(jwkKey.getPublicKeyExponent());
                        RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(modulus, exponent);
                        try {
                            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                            return keyFactory.generatePublic(rsaPublicKeySpec);
                        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                            throw new IllegalStateException("Failed to parse public key");
                        }
                    }));

            keyMap = Collections.unmodifiableMap(newKeys);

        } catch (IOException | NoSuchAlgorithmException | InterruptedException | KeyManagementException e) {
            throw new JwtException("Failed to fetch keys from URL: " + jwkUri, e);
        }
    }

    private BigInteger base64ToBigInteger(String value) {
        return new BigInteger(1, Decoders.BASE64URL.decode(value));
    }
}