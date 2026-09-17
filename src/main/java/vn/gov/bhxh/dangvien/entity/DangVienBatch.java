package vn.gov.bhxh.dangvien.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "DS_DANG_VIEN_BATCH")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DangVienBatch {

    @Id
    @Column(name = "BATCH_ID", nullable = false, length = 100)
    private String batchId;

    @Column(name = "NGAY_CAP_NHAT", nullable = false)
    private OffsetDateTime ngayCapNhat;

    @Column(name = "REQUEST_ID", nullable = false, length = 100)
    private String requestId;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "TOTAL_RECORDS", nullable = false)
    private int totalRecords;

    @Column(name = "ACCEPTED_RECORDS", nullable = false)
    private int acceptedRecords;

    @Column(name = "INVALID_RECORDS", nullable = false)
    private int invalidRecords;

    @Column(name = "DUPLICATE_RECORDS", nullable = false)
    private int duplicateRecords;

    @Column(name = "EXISTING_RECORDS", nullable = false)
    private int existingRecords;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
