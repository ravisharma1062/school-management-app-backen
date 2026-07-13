package com.school.app.auth;

import com.school.app.common.security.JwtService;
import com.school.app.common.security.TenantContext;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthResponse login(LoginRequest request) {
        // authenticate() resolves the tenant as a side effect (UserDetailsServiceImpl sets
        // TenantContext once it finds which school this email belongs to), so the lookup below
        // is already correctly scoped by the time we get here.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        String accessToken = jwtService.generateAccessToken(user, user.getSchoolId());
        String refreshToken = jwtService.generateRefreshToken(user, user.getSchoolId());
        return new AuthResponse(accessToken, refreshToken, user.getRole());
    }

    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        if (!jwtService.isRefreshToken(token)) {
            throw new BadCredentialsException("Provided token is not a refresh token");
        }

        // Trust the school_id already embedded (and signed) in the refresh token itself, rather
        // than re-deriving it — there is no login step here to bootstrap the tenant from.
        UUID schoolId = jwtService.extractSchoolId(token);
        if (schoolId == null) {
            throw new BadCredentialsException("Refresh token is missing tenant information");
        }
        TenantContext.set(schoolId);

        String email = jwtService.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!jwtService.isTokenValid(token, user)) {
            throw new BadCredentialsException("Refresh token is invalid or expired");
        }

        String newAccessToken = jwtService.generateAccessToken(user, user.getSchoolId());
        String newRefreshToken = jwtService.generateRefreshToken(user, user.getSchoolId());
        return new AuthResponse(newAccessToken, newRefreshToken, user.getRole());
    }
}
