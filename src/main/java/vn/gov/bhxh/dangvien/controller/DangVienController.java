package vn.gov.bhxh.dangvien.controller;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import vn.gov.bhxh.dangvien.dto.request.DongBoMoiRequest;
import vn.gov.bhxh.dangvien.dto.response.DongBoMoiResponse;
import vn.gov.bhxh.dangvien.service.DangVienService;

@Slf4j
@RestController
@RequestMapping("/api/v1/dangvien")
public class DangVienController {

    private final DangVienService dangVienService;

    public DangVienController(DangVienService dangVienService) {
        this.dangVienService = dangVienService;
    }

    /**
     * VPTWD chu dong goi API nay moi khi co dang vien moi can dong bo.
     * Luon tra HTTP 202 khi request dung cau truc, ke ca khi co ban ghi REJECTED/EXISTING ben trong.
     *
     * Resilience4j bao ve 2 tang:
     *   @RateLimiter  : Gioi han 20 req/phut -- qua nguong tra 429 ngay
     *   @CircuitBreaker: Oracle lien tuc loi -- ngat mach 30s, tra 503
     *
     * Thu tu annotation: RateLimiter wrap ngoai cung, CircuitBreaker ben trong --
     * dam bao request bi rate-limit khong tinh vao failure count cua CircuitBreaker.
     */
    @RateLimiter(name = "dongBoMoiLimiter", fallbackMethod = "rateLimitFallback")
    @CircuitBreaker(name = "dongBoMoiBreaker", fallbackMethod = "circuitBreakerFallback")
    @PostMapping(value = "/dongbo-moi", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DongBoMoiResponse> dongBoMoi(
            @Valid @RequestBody DongBoMoiRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String xRequestId,
            @RequestHeader(value = "X-Client-Id", required = false) String xClientId) {

        DongBoMoiResponse body = dangVienService.xuLyDongBoMoi(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    /**
     * Fallback khi VPTWD vuot qua gioi han request/phut (RequestNotPermitted).
     * Tra 429 TOO_MANY_REQUESTS ngay lap tuc, khong cham den Oracle.
     */
    public ResponseEntity<DongBoMoiResponse> rateLimitFallback(
            DongBoMoiRequest request, String xRequestId, String xClientId,
            io.github.resilience4j.ratelimiter.RequestNotPermitted ex) {
        log.warn("[RATE-LIMIT] VPTWD vuot gioi han req/phut | batchId={}", request.getBatchId());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    /**
     * Fallback khi CircuitBreaker o trang thai OPEN (Oracle qua tai lien tuc).
     * Tra 503 SERVICE_UNAVAILABLE ngay, khong xep hang doi Oracle.
     */
    public ResponseEntity<DongBoMoiResponse> circuitBreakerFallback(
            DongBoMoiRequest request, String xRequestId, String xClientId,
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.warn("[CIRCUIT-BREAKER] Mach dang OPEN, tu choi request | batchId={}", request.getBatchId());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
