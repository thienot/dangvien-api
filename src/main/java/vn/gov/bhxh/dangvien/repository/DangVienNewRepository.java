package vn.gov.bhxh.dangvien.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.gov.bhxh.dangvien.entity.DangVienNew;

public interface DangVienNewRepository extends JpaRepository<DangVienNew, Long> {

    /** Check socccd da ton tai trong DS_DANG_VIEN_NEW chua (status EXISTING/ALREADY_EXISTS) */
    boolean existsBySocccd(String socccd);

    /**
     * Thực hiện MERGE INTO trong Oracle:
     * - Trả về 1 nếu INSERT thành công (CCCD chưa có -> ACCEPTED)
     * - Trả về 0 nếu CCCD đã tồn tại (không làm gì -> EXISTING)
     */
    @Modifying
    @Query(value = """
        MERGE INTO DS_DANG_VIEN_NEW target
        USING dual ON (target.SOCCCD = :socccd)
        WHEN NOT MATCHED THEN
          INSERT (ID, BATCH_ID, SOCCCD, HOTEN, NGAYSINH, GIOITINH, CREATED_AT)
          VALUES (DS_DANG_VIEN_NEW_SEQ.NEXTVAL, :batchId, :socccd, :hoten, :ngaySinh, :gioiTinh, SYSTIMESTAMP)
        """, nativeQuery = true)
    int mergeDangVien(@Param("batchId") String batchId,
                      @Param("socccd") String socccd,
                      @Param("hoten") String hoten,
                      @Param("ngaySinh") LocalDate ngaySinh,
                      @Param("gioiTinh") String gioiTinh);
}


