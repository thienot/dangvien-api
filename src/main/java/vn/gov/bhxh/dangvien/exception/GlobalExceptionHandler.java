package vn.gov.bhxh.dangvien.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import vn.gov.bhxh.dangvien.dto.response.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Loi nghiep vu tu chu dong nem (BATCH_CONFLICT, INVALID_REQUEST do vuot gioi han...)
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    // Thieu field bat buoc cap request (batch_id/ngay_cap_nhat/data) hoac sai kieu du lieu
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", msg));
    }

    // JSON sai cu phap / khong parse duoc
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "Request body sai dinh dang JSON hoac thieu field bat buoc"));
    }

    // Xung dot du lieu o tang Database:
    // - Neu do 2 request dong thoi trung batch_id (vi pham PK_DS_DANG_VIEN_BATCH) -> Tra ve HTTP 409 BATCH_CONFLICT dung chuan tai lieu
    // - Cac loi xung dot khac -> Tra ve HTTP 500 INTERNAL_ERROR
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        String msg = "";
        if (ex.getMessage() != null) {
            msg += ex.getMessage() + " ";
        }
        if (ex.getRootCause() != null && ex.getRootCause().getMessage() != null) {
            msg += ex.getRootCause().getMessage();
        }

        if (msg.contains("PK_DS_DANG_VIEN_BATCH") || msg.contains("DS_DANG_VIEN_BATCH")) {
            log.warn("[EXCEPTION] Phat hien 2 request dong thoi cung batch_id bi chan boi DB constraint | msg={}", msg);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("BATCH_CONFLICT",
                            "batch_id nay da tung duoc tiep nhan truoc do, vui long doi sang batch_id khac roi gui lai"));
        }

        log.error("[EXCEPTION] DataIntegrityViolationException: {}", msg, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Xung dot du lieu khi ghi vao he thong, vui long thu lai"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("[EXCEPTION] Loi he thong bat ngo, can dieu tra ngay: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Loi he thong phia BHXH, vui long thu lai sau"));
    }
}
