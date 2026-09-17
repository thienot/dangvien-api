package vn.gov.bhxh.dangvien.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import vn.gov.bhxh.dangvien.config.AppProperties;
import vn.gov.bhxh.dangvien.dto.response.ErrorResponse;

/**
 * Xac thuc Authorization: Bearer {token} cho cac API duoi /api/v1/dangvien/**.
 * Token la chuoi TINH, so sanh bang MessageDigest.isEqual de chong Timing Attack.
 */
@Slf4j
@Component
@Order(1)
public class TokenAuthFilter extends OncePerRequestFilter {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenAuthFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String validToken = appProperties.getToken();
        if (validToken == null || validToken.isBlank()) {
            log.error("[AUTH] app.dangvien.token chua duoc cau hinh tren he thong - tu choi moi request");
            writeUnauthorized(response, "Server chua cau hinh token xac thuc");
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            log.warn("[AUTH] Thieu header Authorization hoac sai dinh dang | ip={} | uri={}",
                    getClientIp(request), request.getRequestURI());
            writeUnauthorized(response, "Thieu header Authorization hoac sai dinh dang, phai la 'Bearer <token>'");
            return;
        }

        String token = header.substring("Bearer ".length()).trim();
        if (!MessageDigest.isEqual(validToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[AUTH] Token khong hop le | ip={} | uri={}", getClientIp(request), request.getRequestURI());
            writeUnauthorized(response, "Token khong dung");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Chi kiem tra token voi cac endpoint /api/v1/dangvien/**
        // Tu dong bypass cho /actuator/**, /swagger-ui/**, /v3/api-docs/** neu co sau nay
        return !path.startsWith("/api/v1/dangvien");
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse("UNAUTHORIZED", message)));
    }
}
