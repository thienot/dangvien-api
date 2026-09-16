package vn.gov.bhxh.dangvien.security;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import vn.gov.bhxh.dangvien.config.AppProperties;
import vn.gov.bhxh.dangvien.dto.response.ErrorResponse;

/**
 * Xac thuc Authorization: Bearer {token} cho cac API duoi /api/v1/dangvien/**.
 * Token la chuoi TINH, so sanh voi gia tri cau hinh trong app.dangvien.token (muc 3).
 * AuthHash va rang buoc theo IP nguon CHUA trien khai (theo tai lieu, can trao doi them).
 */
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

        if (!request.getRequestURI().startsWith("/api/v1/dangvien")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "Thieu header Authorization hoac sai dinh dang, phai la 'Bearer <token>'");
            return;
        }

        String token = header.substring("Bearer ".length()).trim();
        if (appProperties.getToken() == null || !appProperties.getToken().equals(token)) {
            writeUnauthorized(response, "Token khong dung");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse("UNAUTHORIZED", message)));
    }
}
