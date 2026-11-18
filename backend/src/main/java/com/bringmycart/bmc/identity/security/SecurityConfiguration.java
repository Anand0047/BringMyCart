package com.bringmycart.bmc.identity.security;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import com.bringmycart.bmc.identity.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                            AuthProperties properties, CsrfCookieFilter csrfCookieFilter,
                                            ExtensionSessionFilter extensionSessionFilter) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(properties.isRefreshCookieSecure()));
        RequestMatcher csrfProtectionMatcher = this::requiresCsrfProtection;
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(csrfProtectionMatcher)
                .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                    @Override
                    public CsrfFilter postProcess(CsrfFilter filter) {
                        // Resource-server configuration adds a blanket Bearer-token CSRF ignore matcher.
                        // Replace the composed matcher so the extension authorization endpoint remains protected.
                        filter.setRequireCsrfProtectionMatcher(csrfProtectionMatcher);
                        return filter;
                    }
                })
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/csrf", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                    "/api/v1/extension-auth/token", "/api/v1/extension-auth/refresh", "/api/v1/extension-auth/logout").permitAll()
                .requestMatchers("/api/v1/identity/admin/**").hasRole(Role.ADMIN.name())
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(jsonAuthenticationEntryPoint()))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                .accessDeniedHandler(jsonAccessDeniedHandler()));
        http.addFilterAfter(csrfCookieFilter, CsrfFilter.class);
        http.addFilterAfter(extensionSessionFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private boolean requiresCsrfProtection(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "TRACE".equals(method) || "OPTIONS".equals(method)) return false;
        String contextPath = request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        if ("/api/v1/auth/register".equals(path) || "/api/v1/auth/login".equals(path)
            || "/api/v1/extension-auth/token".equals(path) || "/api/v1/extension-auth/refresh".equals(path)
            || "/api/v1/extension-auth/logout".equals(path)) return false;
        if ("/api/v1/auth/refresh".equals(path) || "/api/v1/auth/logout".equals(path)
            || "/api/v1/extension-auth/authorize".equals(path)) return true;
        String authorization = request.getHeader("Authorization");
        // Bearer tokens are explicit, non-ambient credentials; cookie-backed website auth remains CSRF-protected.
        return authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.corsOrigins());
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(List.of("Location"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            Collection<GrantedAuthority> authorities = role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
            return authorities;
        });
        return converter;
    }

    private AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, exception) -> writeStatus(response, FORBIDDEN.value(), "Access denied");
    }

    private org.springframework.security.web.AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, exception) -> writeStatus(response, UNAUTHORIZED.value(), "Authentication required");
    }

    private static void writeStatus(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }

    @Bean
    CsrfCookieFilter csrfCookieFilter() {
        return new CsrfCookieFilter();
    }

    @Bean
    ExtensionSessionFilter extensionSessionFilter(com.bringmycart.bmc.identity.application.ExtensionAuthService extensionAuthService) {
        return new ExtensionSessionFilter(extensionAuthService);
    }

    static class ExtensionSessionFilter extends OncePerRequestFilter {
        private final com.bringmycart.bmc.identity.application.ExtensionAuthService extensionAuthService;
        ExtensionSessionFilter(com.bringmycart.bmc.identity.application.ExtensionAuthService extensionAuthService) { this.extensionAuthService = extensionAuthService; }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, IOException {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt
                && jwt.hasClaim("sid")) {
                try {
                    java.util.UUID sessionId = java.util.UUID.fromString(jwt.getClaimAsString("sid"));
                    if (!extensionAuthService.isSessionActive(sessionId)) {
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                        writeStatus(response, UNAUTHORIZED.value(), "Authentication required");
                        return;
                    }
                } catch (IllegalArgumentException exception) {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                    writeStatus(response, UNAUTHORIZED.value(), "Authentication required");
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }

    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        jakarta.servlet.FilterChain filterChain) throws jakarta.servlet.ServletException, IOException {
            Object csrfToken = request.getAttribute(CsrfToken.class.getName());
            if (csrfToken instanceof CsrfToken token) {
                token.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
