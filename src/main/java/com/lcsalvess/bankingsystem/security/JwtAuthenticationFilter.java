package com.lcsalvess.bankingsystem.security;

import com.lcsalvess.bankingsystem.entity.user.User;
import com.lcsalvess.bankingsystem.service.security.CustomUserDetailsService;
import com.lcsalvess.bankingsystem.service.security.JwtClaims;
import com.lcsalvess.bankingsystem.service.security.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Única rota que o filtro ignora: deve coincidir com a rota liberada por
     * {@code permitAll} na {@code SecurityConfig}. Assim, um token vencido
     * reenviado ao login não bloqueia a nova autenticação, e as demais rotas
     * sob {@code /api/v1/auth/} continuam passando pelo filtro.
     */
    private static final PathPatternRequestMatcher LOGIN_REQUEST_MATCHER =
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, "/api/v1/auth/login");

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            RestAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return LOGIN_REQUEST_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length()).trim();

        // "Bearer" sem token equivale a nenhuma credencial: a requisição segue
        // sem autenticação e as rotas protegidas respondem 401 normalmente.
        if (jwt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtClaims claims = jwtService.parse(jwt);

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(claims.username());

            if (!userDetails.isEnabled()) {
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new DisabledException("User is disabled")
                );
                return;
            }

            // Token emitido antes da última troca de senha: não vale mais.
            if (!(userDetails instanceof User user)
                    || user.getTokenVersion() != claims.tokenVersion()) {
                authenticationEntryPoint.commence(
                        request,
                        response,
                        new BadCredentialsException("Invalid authentication")
                );
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder.getContext()
                    .setAuthentication(authentication);

        } catch (JwtException | UsernameNotFoundException exception) {
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid authentication")
            );
            return;
        } catch (DataAccessException | TransactionException exception) {
            // Falha de infraestrutura não é credencial inválida: responder 401
            // faria o cliente descartar um token válido e reautenticar em vão.
            log.error("Could not load the user while authenticating the request.", exception);
            authenticationEntryPoint.commenceServiceUnavailable(response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}