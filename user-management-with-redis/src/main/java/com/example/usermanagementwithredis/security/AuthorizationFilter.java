package com.example.usermanagementwithredis.security;

import com.example.usermanagementwithredis.entities.User;
import com.example.usermanagementwithredis.services.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AuthorizationFilter extends BasicAuthenticationFilter {

    private final Logger logger = LoggerFactory.getLogger(AuthorizationFilter.class);
    private final UserService userService;

    @Value("${security.token-key}")
    private String tokenKey;
    @Value("${security.token-type}")
    private String tokenType;
    @Value("${security.separator}")
    private String separator;

    public AuthorizationFilter(AuthenticationManager authenticationManager, @Lazy UserService userService) {
        super(authenticationManager);
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if(authorizationHeader != null && authorizationHeader.startsWith(tokenType)) {
            UsernamePasswordAuthenticationToken authentication = getAuthentication(request);
            if(authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Don't accept wrong credentials
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } // Accept anonymous authentication, but resources could be locked for the request

        chain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken getAuthentication(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = authorizationHeader.replace(tokenType, "");
        UsernamePasswordAuthenticationToken authentication = null;

        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(tokenKey)
                    .parseClaimsJws(token)
                    .getBody();
            String subject = claims.getSubject();
            String[] subjectData = subject.split(separator);
            if(subjectData.length == 2 && subjectData[0] !=null && subjectData[1] != null) {
                Long id = Long.parseLong(subjectData[0]);
                String email = subjectData[1];
                User user = userService.getByEmail(email);
                if(user != null) {
                    List<GrantedAuthority> authorities = user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getName())).collect(Collectors.toList());
                    authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                }
            }
        } catch (Exception ex) {
            logger.error("Exception captured", ex);
        }

        return authentication;
    }

}
