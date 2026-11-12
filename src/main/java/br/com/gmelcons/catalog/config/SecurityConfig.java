package br.com.gmelcons.catalog.config;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.service.AuditService;
import br.com.gmelcons.catalog.service.LoginAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Autenticação por sessão (cookie HttpOnly + SameSite=Lax), proteção CSRF e RBAC.
 * As regras por URL aqui são a primeira camada; os serviços reforçam com @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Token CSRF em cookie legível pelo JavaScript (HttpOnly=false, necessário para o front enviar o
     * cabeçalho X-XSRF-TOKEN) e com SameSite=Lax, como o cookie de sessão.
     */
    static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuditService audit, ObjectMapper mapper,
                                           LoginAttemptService attempts) throws Exception {
        // Chamadas /api/** sem sessão recebem 401; páginas são redirecionadas ao login.
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(new AntPathRequestMatcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login.html"));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login.html", "/css/**", "/js/**", "/img/**", "/favicon.ico", "/error",
                                "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/users/**", "/api/audit/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/categories/**", "/api/attribute-definitions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**", "/api/attribute-definitions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**", "/api/attribute-definitions/**",
                                "/api/products/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> {
                            attempts.recordSuccess(authentication.getName());
                            audit.logAsFrom(authentication.getName(), request.getRemoteAddr(), AuditOperation.LOGIN, "AppUser",
                                    null, null, null);
                            response.setStatus(HttpStatus.OK.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            mapper.writeValue(response.getWriter(), Map.of("username", authentication.getName()));
                        })
                        .failureHandler((request, response, exception) -> {
                            // Só senha errada conta; conta já bloqueada ou inativa não aumenta o contador.
                            if (exception instanceof BadCredentialsException) {
                                attempts.recordFailure(request.getParameter("username"), request.getRemoteAddr());
                            }
                            audit.logAsFrom(request.getParameter("username"), request.getRemoteAddr(),
                                    AuditOperation.LOGIN_FAILED, "AppUser",
                                    null, null, null);
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            mapper.writeValue(response.getWriter(), Map.of("message", "Usuário ou senha inválidos, ou conta temporariamente bloqueada"));
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                audit.logAs(authentication.getName(), AuditOperation.LOGOUT, "AppUser", null, null, null);
                            }
                            response.setStatus(HttpStatus.OK.value());
                        }))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; style-src 'self'; "
                                        + "script-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicy.SAME_ORIGIN)));

        return http.build();
    }
}
