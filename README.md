# dangvien-api

API BHXH tiếp nhận danh sách đảng viên mới từ VPTWĐ (`POST /api/v1/dangvien/dongbo-moi`).

## 1. Chuẩn bị Oracle

Chạy script tạo bảng trước khi start app:

```sql
@src/main/resources/db/ddl_ds_dang_vien_new.sql
```

## 2. Cấu hình

Sửa trong `application.yml` hoặc set biến môi trường:

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | Tài khoản Oracle | `admin` / `admin` |
| `DANGVIEN_API_TOKEN` | Token tĩnh xác thực Bearer | `33bdfccc-9046-488a-b2f0-2e503df74b1e` (token mẫu trong tài liệu) |
| `DANGVIEN_MAX_RECORDS` | Giới hạn số bản ghi/lần gọi | `1000` |

Sửa `spring.datasource.url` trong `application.yml` cho đúng host/port/service name Oracle thật.

## 3. Chạy

```bash
mvn spring-boot:run
```

## 4. Test bằng curl

```bash
curl -X POST http://localhost:8080/api/v1/dangvien/dongbo-moi \
  -H "Authorization: Bearer 33bdfccc-9046-488a-b2f0-2e503df74b1e" \
  -H "Content-Type: application/json" \
  -d '{
    "batch_id": "BATCH-2026-09-16-001",
    "ngay_cap_nhat": "2026-09-16T10:30:00+07:00",
    "data": [
      { "socccd": "012345678901", "hoten": "Nguyen Van A", "ngaysinh": "1995-05-20", "gioitinh": "1" },
      { "socccd": "012345678901", "hoten": "Trung socccd", "ngaysinh": "1995-05-20", "gioitinh": "1" },
      { "socccd": "abc", "hoten": "Sai socccd", "ngaysinh": "1995-05-20" }
    ]
  }'
```

Kết quả mong đợi: HTTP 202, `accepted_records=1`, `duplicate_records=1` (DUPLICATE_IN_BATCH), `invalid_records=1` (INVALID_SOCCCD).

Gọi lại đúng `batch_id` trên lần nữa → HTTP 409 `BATCH_CONFLICT`.

## 5. Những điểm CHƯA triển khai (theo đúng tài liệu gốc)

- **AuthHash**: thuật toán chưa được xác nhận giữa 2 bên, chưa áp dụng.
- **Ràng buộc theo IP nguồn** (mã lỗi `FORBIDDEN`): chưa triển khai.

## 6. Các tối ưu đã triển khai
- **Khử hoàn toàn Race Condition `socccd`**: Chuyển sang cơ chế atomic `MERGE INTO` trong Oracle DB. Khi 2 request đồng thời chứa cùng `socccd`, request sau tự động nhận `EXISTING` (0 rows affected) mà không bị văng lỗi ORA-00001 (500 INTERNAL_ERROR).
- **Mô hình Master-Detail quản lý Batch (`DS_DANG_VIEN_BATCH`)**: Tách bảng quản lý lô riêng biệt. Kiểm tra trùng `batch_id` trực tiếp trên Primary Key của bảng batch với độ phức tạp O(1), đảm bảo lưu vết 100% mọi đợt tiếp nhận (kể cả lô toàn bộ bản ghi bị REJECTED) và triệt tiêu vấn đề phình index trên bảng chi tiết đảng viên.
- **Bảo mật & Logging**: Chuẩn hóa so sánh token bằng `MessageDigest.isEqual` chống Timing Attack, bổ sung log có cấu trúc chuẩn tag `[DONG-BO-MOI]` và `[AUTH]` ghi nhận IP client.
