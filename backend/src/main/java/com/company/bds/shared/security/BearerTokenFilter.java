package com.company.bds.shared.security;

import com.company.bds.iam.application.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    private static final String NOTIFICATION_STREAM = "/api/v1/notifications/stream";
    private final AuthService authService;
    public BearerTokenFilter(AuthService authService) { this.authService = authService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            // The notification stream reconnects by itself: it proves an open tab, not a person at the keyboard, so it
            // must not reset the idle timeout of a staff session.
            String token = header.substring(7);
            var session = NOTIFICATION_STREAM.equals(request.getRequestURI())
                    ? authService.findSession(token, false) : authService.findSession(token);
            if (session != null) {
                AuthService.UserAccount user = session.user();
                var auth = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                // The session id lets the account mark "this device" in its session list and keep it on password change.
                auth.setDetails(new CurrentUser.SessionDetails(session.id()));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
