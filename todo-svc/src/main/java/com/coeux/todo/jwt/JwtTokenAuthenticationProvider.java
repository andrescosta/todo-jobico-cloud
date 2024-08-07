package com.coeux.todo.jwt;

import java.net.URI;
import java.util.Base64;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.coeux.todo.jwt.jwk.RemoteJwkSigningKeyResolver;
import com.google.gson.internal.LinkedTreeMap;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SigningKeyResolver;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

/* 
 * Inpired by https://github.com/hantsy/spring-webmvc-jwt-sample
*/

@Component
public class JwtTokenAuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenAuthenticationProvider.class);

    private static final String AUTHORITIES_KEY = "roles";

    public static final String HEADER_PREFIX = "Bearer ";


    private JwtParser jwtParser;

    @Value("${jwt.clockSkew:5}")
    long clockSkew;

    @Value("${jwt.jwksURI}")
    URI jwksURI;

    @PostConstruct
    public void init() {
        jwtParser = buildJwtParser(new RemoteJwkSigningKeyResolver(getJwksURI()));
    }

    public Authentication getAuthentication(String token) throws Exception {
        Claims claims = jwtParser
                .parseClaimsJws(token)
                .getBody();

        LinkedTreeMap m = (LinkedTreeMap) claims.get("urn:zitadel:iam:user:metadata");
        if (m==null) {
            throw(new Exception("no problem"));
        }
        String id = (String) m.get("jobico-id");
        var arrs = Base64.getDecoder().decode(id);
        String uuid = new String(java.util.Arrays.copyOfRange(arrs, 1, arrs.length - 1));

        Object authoritiesClaim = claims.get(AUTHORITIES_KEY);

        Collection<? extends GrantedAuthority> authorities = authoritiesClaim == null ? AuthorityUtils.NO_AUTHORITIES
                : AuthorityUtils.commaSeparatedStringToAuthorityList(authoritiesClaim.toString());
        
        User principal = new User(uuid, "", authorities);

        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    public boolean validateRequestToken(String token) {
        try {
            Jws<Claims> claims = jwtParser
                    .parseClaimsJws(token);
            logger.debug("expiration date: {}", claims.getBody().getExpiration());
            return true;
        } catch (Exception e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    private long getClockSkew() {
        return clockSkew;
    }

    private URI getJwksURI() {
        return jwksURI;
    }

    private JwtParser buildJwtParser(SigningKeyResolver resolver) {
        return Jwts
                .parserBuilder()
                .setAllowedClockSkewSeconds(getClockSkew())
                .setSigningKeyResolver(resolver)
                .build();
    }

    public String getAuthToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        return bearerToken.substring(7);
    }

    public boolean isAuthTokenPresent(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        return StringUtils.hasText(bearerToken) && bearerToken.startsWith(HEADER_PREFIX);
    }


}