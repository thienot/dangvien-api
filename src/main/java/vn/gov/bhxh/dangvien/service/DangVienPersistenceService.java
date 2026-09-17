package vn.gov.bhxh.dangvien.service;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.gov.bhxh.dangvien.repository.DangVienNewRepository;

/**
 * Service chịu trách nhiệm ghi từng bản ghi đảng viên vào DB.
 *
 * <p>Mỗi lần gọi {@link #mergeDangVien} đều chạy trong một transaction HOÀN TOÀN ĐỘC LẬP
 * (Propagation.REQUIRES_NEW). Nhờ đó, nếu một dòng gặp lỗi hiếm gặp (ORA-00001 unique
 * constraint violated), chỉ đúng dòng đó bị rollback — toàn bộ các dòng hợp lệ khác
 * trong cùng lô vẫn được commit bình thường.</p>
 *
 * <p>Spring AOP yêu cầu bean này phải là một Spring Bean riêng biệt (không phải inner class
 * hay private method trong cùng class) để proxy REQUIRES_NEW hoạt động đúng.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DangVienPersistenceService {

    private final DangVienNewRepository dangVienNewRepository;

    /**
     * Thực hiện MERGE INTO atomic cho 1 bản ghi đảng viên.
     *
     * @param batchIdPk  ID (Long) của bản ghi Batch cha trong DS_DANG_VIEN_BATCH
     * @param socccd     Số CCCD 12 chữ số
     * @param hoten      Họ tên đảng viên
     * @param ngaySinh   Ngày sinh (đã được parse và validate)
     * @param gioiTinh   Giới tính ('0' hoặc '1', nullable)
     * @return 1 nếu INSERT thành công (ACCEPTED), 0 nếu CCCD đã tồn tại (EXISTING)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int mergeDangVien(Long batchIdPk,
                             String socccd,
                             String hoten,
                             LocalDate ngaySinh,
                             String gioiTinh) {
        log.debug("[DONG-BO-MOI] MERGE batchIdPk={} | socccd={}", batchIdPk, socccd);
        return dangVienNewRepository.mergeDangVien(batchIdPk, socccd, hoten, ngaySinh, gioiTinh);
    }
}
