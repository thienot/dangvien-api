package vn.gov.bhxh.dangvien.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Anh xa bang DS_DANG_VIEN_NEW.
 * Moi dong tuong ung 1 ban ghi dang vien trong data[] cua request,
 * batch_id va ngay_cap_nhat duoc luu lap lai tren tung dong cung lo.
 */
@Entity
@Table(name = "DS_DANG_VIEN_NEW")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DangVienNew {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dsDangVienNewSeq")
    @SequenceGenerator(name = "dsDangVienNewSeq", sequenceName = "DS_DANG_VIEN_NEW_SEQ", allocationSize = 1)
    private Long id;

    @Column(name = "BATCH_ID", nullable = false, length = 100)
    private String batchId;

    @Column(name = "SOCCCD", nullable = false, unique = true, length = 12)
    private String socccd;

    @Column(name = "HOTEN", nullable = false)
    private String hoten;

    @Column(name = "NGAYSINH", nullable = false)
    private LocalDate ngaySinh;

    @Column(name = "GIOITINH", length = 1)
    private String gioiTinh;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
