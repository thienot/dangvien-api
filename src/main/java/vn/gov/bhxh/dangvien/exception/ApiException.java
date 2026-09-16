package vn.gov.bhxh.dangvien.exception;

import lombok.Getter;

/** Loi cap toan bo request (muc 2.1): 400/401/403/409/500 kem error_code rieng */
@Getter
public class ApiException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public ApiException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
