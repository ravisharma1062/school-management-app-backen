package com.school.app.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.app.common.exception.ErrorResponse;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final SchoolRepository schoolRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Wraps the ENTIRE method, not just the JWT-present branch below: a request with no
        // Authorization header (e.g. login) can still populate TenantContext downstream, via
        // UserDetailsServiceImpl's bootstrapping lookup during authenticationManager.authenticate().
        // A pooled thread must never carry one request's tenant into the next.
        try {
            String authHeader = request.getHeader(AUTH_HEADER);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(BEARER_PREFIX.length());
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Set from the already-signature-verified claim so the per-request user lookup
                // below is correctly @TenantId-scoped without an extra bootstrapping query.
                UUID schoolId = jwtService.extractSchoolId(token);
                if (schoolId != null) {
                    TenantContext.set(schoolId);
                }

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(token, userDetails) && !jwtService.isRefreshToken(token)) {
                    if (schoolId != null && shortCircuitForSuspendedSchool(schoolId, request, response)) {
                        return;
                    }
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Scaffold lifecycle enforcement: blocks SUSPENDED/CANCELLED schools outright, flags PAST_DUE
     * ones for the client via a response header. Full grace-period behaviour lands with billing
     * (MT-5); for now this only needs to exist so clients can start handling the error code.
     *
     * @return {@code true} if the request was short-circuited (a 403 body has already been written).
     */
    private boolean shortCircuitForSuspendedSchool(UUID schoolId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        School school = schoolRepository.findById(schoolId).orElse(null);
        if (school == null) {
            return false;
        }
        if (school.getStatus() == SchoolStatus.SUSPENDED || school.getStatus() == SchoolStatus.CANCELLED) {
            writeForbidden(response, request, "SUBSCRIPTION_SUSPENDED",
                    "This school's subscription is " + school.getStatus().name().toLowerCase(java.util.Locale.ROOT)
                            + "; access is blocked.");
            return true;
        }
        if (school.getStatus() == SchoolStatus.PAST_DUE) {
            response.setHeader("X-Subscription-Status", "PAST_DUE");
        }
        return false;
    }

    private void writeForbidden(HttpServletResponse response, HttpServletRequest request, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(
                Instant.now(), HttpServletResponse.SC_FORBIDDEN, "Forbidden", message, request.getRequestURI(), null, code);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
