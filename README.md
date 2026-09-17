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
## 🧪 Test Cases — API `/api/v1/dangvien/dongbo-moi`

> Môi trường test: `localhost:8080` | Token: `Bearer 33bdfccc-9046-488a-b2f0-2e503df74b1e`

### Case 1 — ✅ Lô hợp lệ, tất cả ACCEPTED

**Request:**
```json
POST /api/v1/dangvien/dongbo-moi
{
  "batch_id": "BATCH-2026-001",
  "ngay_cap_nhat": "2026-09-17T08:00:00+07:00",
  "data": [
    { "socccd": "001234567890", "hoten": "Nguyen Van An", "ngaysinh": "1990-05-15", "gioitinh": "1" },
    { "socccd": "001234567891", "hoten": "Tran Thi Binh", "ngaysinh": "1985-11-20", "gioitinh": "0" },
    { "socccd": "001234567892", "hoten": "Le Van Cuong", "ngaysinh": "1978-03-08", "gioitinh": "1" }
  ]
}
```
**Expected:** `HTTP 202` — `accepted: 3`  
**Screenshot:** 
<img width="1920" height="1080" alt="Screenshot (674)" src="https://github.com/user-attachments/assets/1b5a3597-5c92-4050-957d-779257235522" />
<img width="1920" height="1080" alt="Screenshot (675)" src="https://github.com/user-attachments/assets/0549bdec-8653-4979-913d-09bbdde9094c" />
<img width="1920" height="1080" alt="Screenshot (686)" src="https://github.com/user-attachments/assets/b9302220-ed3e-45b0-b20c-d7d890d54e67" />

---

### Case 2 — 🔁 Gửi lại batch_id đã tồn tại → BATCH_CONFLICT

**Request:** _(giống Case 1, cùng `batch_id: BATCH-2026-001`)_  
**Expected:** `HTTP 409` — `error_code: BATCH_CONFLICT`  
**Screenshot:** 
<img width="1920" height="1080" alt="Screenshot (676)" src="https://github.com/user-attachments/assets/53eed42d-6b85-4f0e-9475-694b531a50d3" />

---

### Case 3 — 🔄 Batch mới nhưng CCCD đã tồn tại → EXISTING

**Request:**
```json
POST /api/v1/dangvien/dongbo-moi
{
  "batch_id": "BATCH-2026-002",
  "ngay_cap_nhat": "2026-09-17T09:00:00+07:00",
  "data": [
    { "socccd": "001234567890", "hoten": "Nguyen Van An", "ngaysinh": "1990-05-15", "gioitinh": "1" },
    { "socccd": "001234567893", "hoten": "Pham Thi Dao",  "ngaysinh": "1995-07-22", "gioitinh": "0" }
  ]
}
```
**Expected:** `HTTP 202` — `accepted: 1`, `existing: 1`  
**Screenshot:** 
<img width="1920" height="1080" alt="Screenshot (677)" src="https://github.com/user-attachments/assets/e2c826e4-c7fa-457c-ba26-ec780a6039ed" />
<img width="1920" height="1080" alt="Screenshot (678)" src="https://github.com/user-attachments/assets/e26a7363-937d-491c-b389-94f200fa6730" />
<img width="1920" height="1080" alt="Screenshot (687)" src="https://github.com/user-attachments/assets/a7ad8873-bedf-4371-9ee2-1081cb400af0" />


---

### Case 4 — ⚠️ Lô hỗn hợp (ACCEPTED + REJECTED + DUPLICATE)

**Request:**
```json
POST /api/v1/dangvien/dongbo-moi
{
  "batch_id": "BATCH-2026-003",
  "ngay_cap_nhat": "2026-09-17T10:00:00+07:00",
  "data": [
    { "socccd": "001234567894", "hoten": "Hoang Van Em",         "ngaysinh": "2000-01-01", "gioitinh": "1" },
    { "socccd": "12345",        "hoten": "Nguyen Van F",         "ngaysinh": "1980-06-10", "gioitinh": "1" },
    { "socccd": "001234567895", "hoten": "Vu Thi Giang",         "ngaysinh": "30/02/1990", "gioitinh": "0" },
    { "socccd": "001234567894", "hoten": "Hoang Van Em (ban sao)","ngaysinh": "2000-01-01", "gioitinh": "1" }
  ]
}
```

| CCCD | Kết quả | Lý do |
|---|---|---|
| `001234567894` | `ACCEPTED` ✅ | Hợp lệ, mới |
| `12345` | `REJECTED / INVALID_SOCCCD` ❌ | Không đủ 12 chữ số |
| `001234567895` | `REJECTED / INVALID_DATA` ❌ | Ngày 30/02 không tồn tại |
| `001234567894` _(lần 2)_ | `REJECTED / DUPLICATE_IN_BATCH` ❌ | Trùng trong cùng lô |

**Expected:** `HTTP 202` — `accepted: 1`, `invalid: 3`  
**Screenshot:** 
<img width="1920" height="1080" alt="Screenshot (679)" src="https://github.com/user-attachments/assets/4926eeb3-6cfe-4fa2-b53d-8bccf0593441" />
<img width="1920" height="1080" alt="Screenshot (680)" src="https://github.com/user-attachments/assets/63ed573c-e1bd-4abb-8b98-8318c9d40d13" />
<img width="1920" height="1080" alt="Screenshot (688)" src="https://github.com/user-attachments/assets/b28bf972-de05-428c-8378-f68980283374" />

---

### Case 5 — 🚫 Thiếu token → 401 UNAUTHORIZED

**Request:** Xóa header `Authorization`  
**Expected:** `HTTP 401` — `error_code: UNAUTHORIZED`  
**Screenshot:**
<img width="1920" height="1080" alt="Screenshot (681)" src="https://github.com/user-attachments/assets/6a61f0b4-e76c-493b-b6cc-267e4bebbeac" />

### Case 6— 1000 record
**Screenshot:**
<img width="1920" height="1080" alt="Screenshot (669)" src="https://github.com/user-attachments/assets/9c291f97-77e2-41fe-9190-36d77b6aaca6" />
<img width="1920" height="1080" alt="Screenshot (671)" src="https://github.com/user-attachments/assets/d0bd2447-a88e-423e-9a43-5eea14a4bdfd" />
<img width="1920" height="1080" alt="Screenshot (689)" src="https://github.com/user-attachments/assets/5142d5cc-5bff-4765-b7ae-2316bb5ee914" />
<img width="1920" height="1080" alt="Screenshot (690)" src="https://github.com/user-attachments/assets/11dddd7f-fcfd-418d-9caa-fcd62a9587be" />

### Case 7— 1001 record
**Screenshot:**

<img width="1920" height="1080" alt="Screenshot (672)" src="https://github.com/user-attachments/assets/b7272b53-195d-43ef-b00e-4f882b0c5372" />
<img width="1920" height="1080" alt="Screenshot (673)" src="https://github.com/user-attachments/assets/2398027a-62c9-4b1d-808f-1ceca977078a" />
