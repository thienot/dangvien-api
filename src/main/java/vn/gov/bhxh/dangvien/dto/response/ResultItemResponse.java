package vn.gov.bhxh.dangvien.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ResultItemResponse {

    private String socccd;

    /** ACCEPTED / REJECTED / EXISTING */
    private String status;

    /** null khi ACCEPTED */
    @JsonProperty("error_code")
    private String errorCode;

    private String message;
}
