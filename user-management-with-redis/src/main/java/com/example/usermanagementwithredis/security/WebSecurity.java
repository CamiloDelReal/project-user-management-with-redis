package com.example.usermanagementwithredis.security;

import com.example.usermanagementwithredis.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurity extends WebSecurityConfigurerAdapter {

    private final UserService userService;

    @Autowired
    public WebSecurity(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable();
        http.authorizeRequests()
                .antMatchers("/login", "/users").permitAll()
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .addFilter(provideAuthorizationFilter());
    }

    @Bean
    public AuthorizationFilter provideAuthorizationFilter() throws Exception {
        return new AuthorizationFilter(authenticationManager(), userService);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService()).passwordEncoder(provideBCryptPasswordEncoder());
        super.configure(auth);
    }

    @Bean
    public AuthenticationManager provideAuthenticationManager() throws Exception {
        return authenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder provideBCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
