package vn.gov.bhxh.dangvien.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DongBoMoiResponse {

    @JsonProperty("batch_id")
    private String batchId;

    /** Luon la "RECEIVED" khi HTTP 202 */
    private String status;

    @JsonProperty("total_records")
    private int totalRecords;

    @JsonProperty("accepted_records")
    private int acceptedRecords;

    @JsonProperty("invalid_records")
    private int invalidRecords;

    @JsonProperty("duplicate_records")
    private int duplicateRecords;

    @JsonProperty("existing_records")
    private int existingRecords;

    /** Do BHXH tu sinh, KHONG phai X-Request-Id cua VPTWD gui len */
    @JsonProperty("request_id")
    private String requestId;

    private List<ResultItemResponse> results;
}
