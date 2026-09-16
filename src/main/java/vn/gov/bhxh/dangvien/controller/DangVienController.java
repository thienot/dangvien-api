package vn.gov.bhxh.dangvien.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import vn.gov.bhxh.dangvien.dto.request.DongBoMoiRequest;
import vn.gov.bhxh.dangvien.dto.response.DongBoMoiResponse;
import vn.gov.bhxh.dangvien.service.DangVienService;

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
     */
    @PostMapping(value = "/dongbo-moi", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DongBoMoiResponse> dongBoMoi(
            @Valid @RequestBody DongBoMoiRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String xRequestId,
            @RequestHeader(value = "X-Client-Id", required = false) String xClientId) {

        DongBoMoiResponse body = dangVienService.xuLyDongBoMoi(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }
}
