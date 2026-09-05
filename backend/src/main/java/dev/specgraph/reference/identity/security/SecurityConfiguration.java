package dev.specgraph.reference.identity.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Compatibility security chain for pre-R4 checkpoints where authentication was not yet active.
 * It is intentionally profile-exclusive with the real operator security chain.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!r4 & !r4-auth")
class LegacySecurityConfiguration {
    @Bean
    SecurityFilterChain legacySecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}

/**
 * Demonstration security boundary with two BCrypt-backed local operators, session CSRF protection
 * and authentication for every application API. Public assets, health and session discovery remain
 * reachable so a browser can establish the login flow.
 */
@Configuration(proxyBeanMethods = false)
@Profile("r4 | r4-auth")
class R4SecurityConfiguration {
    static final String OPERATOR_ALPHA = "operator-alpha";
    static final String OPERATOR_BETA = "operator-beta";
    static final String PASSWORD_ALPHA = "alpha-demo-2026";
    static final String PASSWORD_BETA = "beta-demo-2026";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService demoOperators(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(OPERATOR_ALPHA)
                        .password(passwordEncoder.encode(PASSWORD_ALPHA))
                        .roles("OPERATOR")
                        .build(),
                User.withUsername(OPERATOR_BETA)
                        .password(passwordEncoder.encode(PASSWORD_BETA))
                        .roles("OPERATOR")
                        .build());
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    /**
     * Keeps session establishment and static discovery public while protecting every application API
     * and retaining CSRF checks for state-changing browser requests.
     */
    @Bean
    SecurityFilterChain r4Security(HttpSecurity http, CsrfTokenRepository csrfTokenRepository) throws Exception {
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/session").permitAll()
                        .requestMatchers("/api/session/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(form -> form
                        .loginProcessingUrl("/api/session/login")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .failureHandler((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .logout(logout -> logout
                        .logoutUrl("/api/session/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .build();
    }
}
