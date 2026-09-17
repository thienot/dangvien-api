package vn.gov.bhxh.dangvien.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.gov.bhxh.dangvien.entity.DangVienBatch;

@Repository
public interface DangVienBatchRepository extends JpaRepository<DangVienBatch, String> {
}
