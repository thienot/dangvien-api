package vn.gov.bhxh.dangvien.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import vn.gov.bhxh.dangvien.config.AppProperties;
import vn.gov.bhxh.dangvien.dto.request.DangVienItemRequest;
import vn.gov.bhxh.dangvien.dto.request.DongBoMoiRequest;
import vn.gov.bhxh.dangvien.dto.response.DongBoMoiResponse;
import vn.gov.bhxh.dangvien.dto.response.ResultItemResponse;
import vn.gov.bhxh.dangvien.entity.DangVienBatch;
import vn.gov.bhxh.dangvien.exception.ApiException;
import vn.gov.bhxh.dangvien.repository.DangVienBatchRepository;

@Slf4j
@Service
public class DangVienService {

    // Dùng pattern 'uuuu-MM-dd' kết hợp ResolverStyle.STRICT để chặn đứng mọi ngày không có thật trong lịch
    private static final DateTimeFormatter NGAYSINH_FORMAT = 
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final int MIN_BIRTH_YEAR = 1900;

    private final DangVienBatchRepository batchRepository;
    private final DangVienPersistenceService persistenceService;
    private final AppProperties appProperties;

    public DangVienService(DangVienBatchRepository batchRepository,
                           DangVienPersistenceService persistenceService,
                           AppProperties appProperties) {
        this.batchRepository = batchRepository;
        this.persistenceService = persistenceService;
        this.appProperties = appProperties;
    }

    public DongBoMoiResponse xuLyDongBoMoi(DongBoMoiRequest request) {

        log.info("[DONG-BO-MOI] Tiep nhan batchId={} | ngayCapNhat={} | tongSoBanGhi={}",
                request.getBatchId(), request.getNgayCapNhat(), request.getData().size());

        // Muc 4: vuot gioi han so ban ghi/lan goi -> 400 INVALID_REQUEST
        if (request.getData().size() > appProperties.getMaxRecords()) {
            log.warn("[DONG-BO-MOI] Tu choi batchId={} | Ly do: vuot qua gioi han {} ban ghi (gui len: {})",
                    request.getBatchId(), appProperties.getMaxRecords(), request.getData().size());
            throw new ApiException("INVALID_REQUEST",
                    "So ban ghi trong data[] vuot qua gioi han " + appProperties.getMaxRecords() + " ban ghi/lan goi",
                    400);
        }

        // Kiem tra tren bang master DS_DANG_VIEN_BATCH -> O(1) theo UK BATCH_ID, khong lo mat dau batch
        if (batchRepository.existsByBatchId(request.getBatchId())) {
            log.warn("[DONG-BO-MOI] Tu choi batchId={} | Ly do: BATCH_CONFLICT - batch_id da tung duoc tiep nhan truoc do",
                    request.getBatchId());
            throw new ApiException("BATCH_CONFLICT",
                    "batch_id nay da tung duoc tiep nhan truoc do, vui long doi sang batch_id khac roi gui lai",
                    409);
        }

        String requestId = UUID.randomUUID().toString();

        // Luu truoc ban ghi Batch voi trang thai PROCESSING de lay ID tu dong tang thoa man khoa ngoai
        DangVienBatch batch = DangVienBatch.builder()
                .batchId(request.getBatchId())
                .ngayCapNhat(request.getNgayCapNhat())
                .requestId(requestId)
                .status("PROCESSING")
                .totalRecords(request.getData().size())
                .acceptedRecords(0)
                .invalidRecords(0)
                .duplicateRecords(0)
                .existingRecords(0)
                .build();
        batch = batchRepository.saveAndFlush(batch);
        Long batchIdPk = batch.getId();

        List<ResultItemResponse> results = new ArrayList<>(request.getData().size());
        Set<String> seenInBatch = new HashSet<>();

        int accepted = 0;
        int invalid = 0;
        int duplicate = 0;
        int existing = 0;
        int mergeError = 0;

        for (DangVienItemRequest item : request.getData()) {
            String socccd = item.getSocccd();

            // 3.1: socccd bat buoc dung 12 chu so
            if (socccd == null || !socccd.matches("\\d{12}")) {
                results.add(new ResultItemResponse(socccd, "REJECTED", "INVALID_SOCCCD",
                        "socccd phai gom dung 12 chu so"));
                invalid++;
                continue;
            }

            LocalDate ngaySinh = parseNgaySinh(item.getNgaysinh());
            String loiDuLieu = validateDuLieu(item, ngaySinh);
            if (loiDuLieu != null) {
                results.add(new ResultItemResponse(socccd, "REJECTED", "INVALID_DATA", loiDuLieu));
                invalid++;
                continue;
            }

            // Trung socccd trong CUNG 1 lo: giu ban ghi dau tien, cac ban ghi sau bi loai
            if (!seenInBatch.add(socccd)) {
                results.add(new ResultItemResponse(socccd, "REJECTED", "DUPLICATE_IN_BATCH",
                        "socccd bi lap lai nhieu hon 1 lan trong cung lo, chi ban ghi dau tien duoc xu ly"));
                duplicate++;
                continue;
            }

            // Thuc hien atomic MERGE INTO qua service con REQUIRES_NEW:
            // - Moi ban ghi chay trong transaction doc lap -> loi 1 dong KHONG rollback toan lo
            // - Tra ve 1: them moi thanh cong (ACCEPTED)
            // - Tra ve 0: CCCD da ton tai tu truoc (EXISTING)
            // - Nem exception: loi hiem gap ORA-00001 (2 session race condition cung socccd chua ton tai)
            try {
                int rowsAffected = persistenceService.mergeDangVien(
                        batchIdPk,
                        socccd,
                        item.getHoten(),
                        ngaySinh,
                        item.getGioitinh()
                );

                if (rowsAffected == 1) {
                    results.add(new ResultItemResponse(socccd, "ACCEPTED", null, "Da tao ho so moi thanh cong"));
                    accepted++;
                } else {
                    results.add(new ResultItemResponse(socccd, "EXISTING", "ALREADY_EXISTS",
                            "socccd da ton tai, he thong khong tao them ban ghi"));
                    existing++;
                }
            } catch (DataIntegrityViolationException ex) {
                // Race condition cuc hiem: 2 request dong thoi merge cung socccd chua ton tai
                // -> ORA-00001. Transaction rieng cua dong nay da bi rollback, log lai va tiep tuc voi dong tiep theo.
                log.warn("[DONG-BO-MOI] Race-condition ORA-00001 batchId={} socccd={}: {}",
                        request.getBatchId(), socccd, ex.getMessage());
                results.add(new ResultItemResponse(socccd, "REJECTED", "MERGE_CONFLICT",
                        "Xung dot du lieu khi ghi dong thoi, vui long gui lai ban ghi nay"));
                mergeError++;
            }
        }

        // Cap nhat so lieu thong ke va trang thai RECEIVED cho ban ghi Batch
        // mergeError duoc gop vao invalidRecords de don gian hoa API response
        batch.setAcceptedRecords(accepted);
        batch.setInvalidRecords(invalid + mergeError);
        batch.setDuplicateRecords(duplicate);
        batch.setExistingRecords(existing);
        batch.setStatus("RECEIVED");
        batchRepository.save(batch);

        DongBoMoiResponse response = DongBoMoiResponse.builder()
                .batchId(request.getBatchId())
                .status("RECEIVED")
                .totalRecords(request.getData().size())
                .acceptedRecords(accepted)
                .invalidRecords(invalid)
                .duplicateRecords(duplicate)
                .existingRecords(existing)
                .requestId(requestId)
                .results(results)
                .build();

        log.info("[DONG-BO-MOI] Hoan thanh batchId={} | requestId={} | tongSo={} | accepted={} | existing={} | duplicate={} | invalid={} | mergeError={}",
                request.getBatchId(), requestId, request.getData().size(), accepted, existing, duplicate, invalid, mergeError);

        return response;
    }

    private LocalDate parseNgaySinh(String ngaysinh) {
        if (ngaysinh == null) {
            return null;
        }
        try {
            return LocalDate.parse(ngaysinh, NGAYSINH_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String validateDuLieu(DangVienItemRequest item, LocalDate ngaySinh) {
        if (item.getHoten() == null || item.getHoten().trim().isEmpty()) {
            return "hoten khong duoc de trong";
        }
        if (ngaySinh == null) {
            return "ngaysinh sai dinh dang, yeu cau yyyy-MM-dd";
        }
        int year = ngaySinh.getYear();
        if (year < MIN_BIRTH_YEAR || year > LocalDate.now().getYear()) {
            return "nam sinh phai trong khoang " + MIN_BIRTH_YEAR + " den nam hien tai";
        }
        String gioiTinh = item.getGioitinh();
        if (gioiTinh != null && !gioiTinh.isEmpty() && !gioiTinh.equals("0") && !gioiTinh.equals("1")) {
            return "gioitinh chi nhan gia tri '0' (Nu) hoac '1' (Nam)";
        }
        return null;
    }
}
