package dev.playsense.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionService sessions;

    public AuthInterceptor(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String id = Optional.ofNullable(WebUtils.getCookie(request, SessionService.COOKIE))
                .map(cookie -> cookie.getValue())
                .orElse(null);

        Optional<Long> steamid = sessions.resolve(id);
        if (steamid.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthenticated\"}");
            return false;
        }

        request.setAttribute("steamid", steamid.get());
        return true;
    }
}
