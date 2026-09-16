package vn.gov.bhxh.dangvien.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.gov.bhxh.dangvien.entity.DangVienNew;

public interface DangVienNewRepository extends JpaRepository<DangVienNew, Long> {

    /** Check batch_id da tung duoc tiep nhan chua (muc 4: batch_id phai duy nhat) */
    boolean existsByBatchId(String batchId);

    /** Check socccd da ton tai trong DS_DANG_VIEN_NEW chua (status EXISTING/ALREADY_EXISTS) */
    boolean existsBySocccd(String socccd);
}
