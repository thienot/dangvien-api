package vn.gov.bhxh.dangvien.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {"error_code": "...", "message": "..."} - dung cho loi cap toan bo request (muc 2.1) */
@Getter
@AllArgsConstructor
public class ErrorResponse {

    @JsonProperty("error_code")
    private String errorCode;

    private String message;
}
