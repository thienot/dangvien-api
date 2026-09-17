package vn.gov.bhxh.dangvien.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.gov.bhxh.dangvien.entity.DangVienBatch;

@Repository
public interface DangVienBatchRepository extends JpaRepository<DangVienBatch, Long> {

    /** Kiểm tra mã batch_id do VPTWĐ gửi lên đã từng được tiếp nhận chưa */
    boolean existsByBatchId(String batchId);

    /** Tìm lô theo batch_id */
    Optional<DangVienBatch> findByBatchId(String batchId);
}

