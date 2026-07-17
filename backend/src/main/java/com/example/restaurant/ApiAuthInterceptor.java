package com.example.restaurant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class ApiAuthInterceptor implements HandlerInterceptor {
    private static final Set<String> MANAGER_PREFIXES = Set.of(
            "/api/staff",
            "/api/reports",
            "/api/vips"
    );

    private final AuthService authService;

    public ApiAuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || "/api/login".equals(path)) {
            return true;
        }

        var sessionUser = authService.authenticate(request.getHeader("Authorization"));
        if (sessionUser.isEmpty()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return false;
        }

        if (requiresManager(request) && !authService.isManager(sessionUser.get())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "没有权限执行该操作");
            return false;
        }

        request.setAttribute("currentUser", sessionUser.get());
        return true;
    }

    private boolean requiresManager(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod()))
                && (path.startsWith("/api/tables") || path.startsWith("/api/dishes") || path.startsWith("/api/categories"))) {
            return true;
        }
        return MANAGER_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
