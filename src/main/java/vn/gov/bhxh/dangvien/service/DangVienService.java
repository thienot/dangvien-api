package vn.gov.bhxh.dangvien.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.gov.bhxh.dangvien.config.AppProperties;
import vn.gov.bhxh.dangvien.dto.request.DangVienItemRequest;
import vn.gov.bhxh.dangvien.dto.request.DongBoMoiRequest;
import vn.gov.bhxh.dangvien.dto.response.DongBoMoiResponse;
import vn.gov.bhxh.dangvien.dto.response.ResultItemResponse;
import vn.gov.bhxh.dangvien.entity.DangVienBatch;
import vn.gov.bhxh.dangvien.exception.ApiException;
import vn.gov.bhxh.dangvien.repository.DangVienBatchRepository;
import vn.gov.bhxh.dangvien.repository.DangVienNewRepository;

@Slf4j
@Service
public class DangVienService {

    private static final DateTimeFormatter NGAYSINH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MIN_BIRTH_YEAR = 1900;

    private final DangVienNewRepository repository;
    private final DangVienBatchRepository batchRepository;
    private final AppProperties appProperties;

    public DangVienService(DangVienNewRepository repository,
                           DangVienBatchRepository batchRepository,
                           AppProperties appProperties) {
        this.repository = repository;
        this.batchRepository = batchRepository;
        this.appProperties = appProperties;
    }

    @Transactional
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

        // Kiem tra tren bang master DS_DANG_VIEN_BATCH -> O(1) theo PK, khong lo mat dau batch
        if (batchRepository.existsById(request.getBatchId())) {
            log.warn("[DONG-BO-MOI] Tu choi batchId={} | Ly do: BATCH_CONFLICT - batch_id da tung duoc tiep nhan truoc do",
                    request.getBatchId());
            throw new ApiException("BATCH_CONFLICT",
                    "batch_id nay da tung duoc tiep nhan truoc do, vui long doi sang batch_id khac roi gui lai",
                    409);
        }

        String requestId = UUID.randomUUID().toString();

        // Luu truoc ban ghi Batch voi trang thai PROCESSING de thoa man khoa ngoai FK_DS_DANG_VIEN_NEW_BATCH
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
        batchRepository.saveAndFlush(batch);

        List<ResultItemResponse> results = new ArrayList<>(request.getData().size());
        Set<String> seenInBatch = new HashSet<>();

        int accepted = 0;
        int invalid = 0;
        int duplicate = 0;
        int existing = 0;

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

            // Thuc hien atomic MERGE INTO (bang con DS_DANG_VIEN_NEW da co FK tro ve DS_DANG_VIEN_BATCH):
            // - Tra ve 1: them moi thanh cong (ACCEPTED)
            // - Tra ve 0: CCCD da ton tai tu truoc (EXISTING)
            int rowsAffected = repository.mergeDangVien(
                    request.getBatchId(),
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
        }

        // Cap nhat so lieu thong ke va trang thai RECEIVED cho ban ghi Batch
        batch.setAcceptedRecords(accepted);
        batch.setInvalidRecords(invalid);
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

        log.info("[DONG-BO-MOI] Hoan thanh batchId={} | requestId={} | tongSo={} | accepted={} | existing={} | duplicate={} | invalid={}",
                request.getBatchId(), requestId, request.getData().size(), accepted, existing, duplicate, invalid);

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
