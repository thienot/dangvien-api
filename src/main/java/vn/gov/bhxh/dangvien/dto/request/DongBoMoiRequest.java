package vn.gov.bhxh.dangvien.dto.request;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DongBoMoiRequest {

    @NotBlank(message = "batch_id khong duoc de trong")
    @JsonProperty("batch_id")
    private String batchId;

    @NotNull(message = "ngay_cap_nhat khong duoc de trong, dinh dang ISO-8601 co offset")
    @JsonProperty("ngay_cap_nhat")
    private OffsetDateTime ngayCapNhat;

    @NotEmpty(message = "data khong duoc rong")
    @Valid
    private List<DangVienItemRequest> data;
}
