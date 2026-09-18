# dangvien-api
REST API nội bộ tiếp nhận và đồng bộ dữ liệu **Đảng viên mới** từ hệ thống VPTWĐ vào hệ thống BHXH.

API cung cấp endpoint để VPTWĐ gửi dữ liệu Đảng viên mới theo từng batch. Hệ thống thực hiện xác thực request, kiểm tra dữ liệu đầu vào, phát hiện dữ liệu trùng trong cùng batch, kiểm tra dữ liệu đã tồn tại và ghi nhận dữ liệu hợp lệ vào Oracle.

API xử lý theo cơ chế **synchronous request** và trả kết quả ngay sau khi hoàn tất việc tiếp nhận/xử lý batch.

## Tech stack

|                 |                                                                            |
| --------------- | -------------------------------------------------------------------------- |
| Language        | Java 21                                                                    |
| Framework       | Spring Boot                                                                |
| Database        | Oracle + Spring Data JPA / Hibernate                                       |
| Database Driver | Oracle JDBC (`ojdbc11`)                                                    |
| Security        | Spring Security — stateless, xác thực bằng `Authorization: Bearer <token>` |
| API             | REST API                                                                   |
| Validation      | Jakarta Bean Validation                                                    |
| Resilience      | Resilience4j — Rate Limiter + Circuit Breaker                              |
| Monitoring      | Spring Boot Actuator                                                       |
| Port            | **8080**                                                                   |

## API

### Đồng bộ Đảng viên mới

```http
POST /api/v1/dangvien/dongbo-moi
```

API được VPTWĐ chủ động gọi để gửi danh sách Đảng viên mới sang hệ thống BHXH.

### Request headers

| Header          | Required | Description                                                         |
| --------------- | -------- | ------------------------------------------------------------------- |
| `Authorization` | Yes      | Bearer token dùng để xác thực request                               |
| `Content-Type`  | Yes      | `application/json`                                                  |
| `X-Request-Id`  | No       | Request ID do phía client gửi, dùng cho correlation/tracing nếu cần |
| `X-Client-Id`   | No       | Định danh client                                                    |

### Request body

Request gồm:

* `batch_id`: mã batch, bắt buộc và phải unique trên toàn hệ thống.
* `ngay_cap_nhat`: thời điểm cập nhật dữ liệu, định dạng ISO-8601 có offset.
* `data`: danh sách Đảng viên, bắt buộc và tối đa **1000 records/batch**.

Mỗi record gồm:

| Field      | Required | Rule                                       |
| ---------- | -------- | ------------------------------------------ |
| `socccd`   | Yes      | Đúng 12 chữ số                             |
| `hoten`    | Yes      | Họ và tên                                  |
| `ngaysinh` | Yes      | `yyyy-MM-dd`, năm từ 1900 đến năm hiện tại |
| `gioitinh` | No       | Chỉ nhận `"0"` hoặc `"1"`                  |

## Business processing

Sau khi nhận request, hệ thống thực hiện các bước chính:

```text
Client / VPTWĐ
      |
      v
Authentication
      |
      v
Request Validation
      |
      v
Batch ID Validation
      |
      v
Check Duplicate In Batch
      |
      v
Check Existing Data
      |
      v
Persist Valid Records
      |
      v
Build Response
      |
      v
HTTP 202 Accepted
```

### Kết quả xử lý từng record

Mỗi record trong `data[]` được trả về kết quả theo đúng thứ tự input:

| Status     | Ý nghĩa                                    |
| ---------- | ------------------------------------------ |
| `ACCEPTED` | Dữ liệu hợp lệ và được tiếp nhận           |
| `REJECTED` | Dữ liệu không hợp lệ                       |
| `EXISTING` | `SOCCCD` đã tồn tại trong dữ liệu hệ thống |

Các lỗi chính:

| Error code           | Ý nghĩa                                 |
| -------------------- | --------------------------------------- |
| `INVALID_SOCCCD`     | `SOCCCD` không đúng định dạng 12 chữ số |
| `INVALID_DATA`       | Dữ liệu record không hợp lệ             |
| `DUPLICATE_IN_BATCH` | `SOCCCD` bị trùng trong cùng batch      |
| `ALREADY_EXISTS`     | `SOCCCD` đã tồn tại                     |

Đối với duplicate trong cùng batch, **record xuất hiện đầu tiên được xử lý trước; các record trùng phía sau bị đánh dấu `DUPLICATE_IN_BATCH`**.

## HTTP Response

Với request có cấu trúc hợp lệ, API trả:

```http
202 Accepted
```

Response chứa các thông tin tổng hợp:

| Field               | Description                                        |
| ------------------- | -------------------------------------------------- |
| `batch_id`          | Mã batch của request                               |
| `status`            | Trạng thái tiếp nhận batch, hiện tại là `RECEIVED` |
| `total_records`     | Tổng số records trong request                      |
| `accepted_records`  | Số records ACCEPTED                                |
| `invalid_records`   | Số records REJECTED                                |
| `duplicate_records` | Số records duplicate trong batch                   |
| `existing_records`  | Số records đã tồn tại                              |
| `request_id`        | Request ID do hệ thống BHXH sinh                   |
| `results`           | Kết quả xử lý từng record                          |

`results[]` được trả về **theo đúng thứ tự của `data[]` trong request**.

## Error handling

Các lỗi ở cấp request:

| HTTP Status | Error Code        | Trường hợp                                                         |
| ----------- | ----------------- | ------------------------------------------------------------------ |
| `400`       | `INVALID_REQUEST` | JSON không hợp lệ, thiếu field bắt buộc, vượt quá 1000 records,... |
| `401`       | `UNAUTHORIZED`    | Thiếu hoặc sai Bearer token                                        |
| `403`       | `FORBIDDEN`       | Dành cho cơ chế IP restriction                                     |
| `409`       | `BATCH_CONFLICT`  | `batch_id` đã được nhận trước đó                                   |
| `500`       | `INTERNAL_ERROR`  | Lỗi hệ thống                                                       |

## Bảo mật

API sử dụng **stateless authentication** thông qua HTTP header:

```http
Authorization: Bearer <token>
```

Các request tới API phải được xác thực trước khi thực hiện business processing.

Token được cấu hình thông qua application configuration/environment variable thay vì hard-code khi triển khai production.

> Lưu ý: giá trị token mặc định trong môi trường local/dev không được sử dụng cho production.

## Resilience

API sử dụng Resilience4j để kiểm soát traffic và bảo vệ hệ thống khi downstream/database có dấu hiệu quá tải hoặc lỗi.

### Rate Limiter

Rate Limiter hiện tại được cấu hình:

```yaml
limit-for-period: 20
limit-refresh-period: 60s
timeout-duration: 0s
```

Ý nghĩa:

* Cho phép tối đa **20 requests trong mỗi 60 giây trên mỗi instance**.
* Request vượt quá giới hạn bị từ chối ngay.
* `timeout-duration: 0s` nghĩa là không xếp request vào hàng đợi để chờ quota.
* Giá trị rate limit có thể cấu hình thông qua environment variable `RATE_LIMIT_PER_MINUTE`.

### Circuit Breaker

Circuit Breaker theo dõi trạng thái xử lý của API:

```text
CLOSED
   |
   | Failure / Slow call vượt threshold
   v
 OPEN
   |
   | Sau 30s
   v
HALF_OPEN
   |
   | Trial calls
   |
   +---- Success ----> CLOSED
   |
   +---- Failure ----> OPEN
```

Configuration hiện tại:

| Configuration             |    Value |
| ------------------------- | -------: |
| Sliding window            | 10 calls |
| Minimum calls             |        5 |
| Failure threshold         |      50% |
| Slow call threshold       |      80% |
| Slow call duration        |      10s |
| Open state duration       |      30s |
| Half-open permitted calls |        3 |

Mục đích của hai cơ chế:

* **Rate Limiter:** kiểm soát lượng request đi vào hệ thống.
* **Circuit Breaker:** ngăn hệ thống tiếp tục gọi/xử lý khi dependency hoặc processing có dấu hiệu lỗi hoặc quá chậm.


## Database

Dữ liệu được lưu trữ trên Oracle thông qua:

```text
Spring Data JPA
       |
   Hibernate
       |
   Oracle JDBC
       |
     Oracle
```

Database chịu trách nhiệm bảo vệ các constraint quan trọng, đặc biệt đối với dữ liệu có yêu cầu unique.

Trong trường hợp concurrent request cùng gửi một `SOCCCD`, database constraint được sử dụng như một lớp bảo vệ consistency cuối cùng. Application cần xử lý phù hợp trường hợp unique constraint violation thay vì coi mọi `ORA-00001` là cùng một loại lỗi nghiệp vụ.

## Validation & duplicate handling

API thực hiện validation ở nhiều mức:

```text
Request validation
      |
      +-- batch_id
      +-- ngay_cap_nhat
      +-- data size <= 1000
      |
      v
Record validation
      |
      +-- SOCCCD
      +-- Họ tên
      +-- Ngày sinh
      +-- Giới tính
      |
      v
Duplicate detection
      |
      +-- Duplicate trong cùng batch
      +-- Existing data
      |
      v
Database constraint
```

Việc kiểm tra duplicate trong batch được thực hiện trước khi persist để tránh xử lý lặp lại cùng một `SOCCCD`.


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

### Case 8 — 🔁 Trùng CCCD trong cùng batch → DUPLICATE_IN_BATCH
**Mô tả:**
Kiểm tra trường hợp cùng một `socccd` xuất hiện nhiều lần trong cùng một batch. Theo rule xử lý, lần xuất hiện đầu tiên được xử lý bình thường; các lần xuất hiện tiếp theo phải bị từ chối với `DUPLICATE_IN_BATCH`.
Case này nhằm kiểm tra logic phát hiện duplicate **trong phạm vi một batch**, đồng thời xác nhận chỉ có một bản ghi hợp lệ được ghi vào DB.
**Expected:**
| STT | CCCD           | Kết quả                           | Lý do                       |
| --- | -------------- | --------------------------------- | --------------------------- |
| 1   | `001234567896` | `ACCEPTED` ✅                      | Hợp lệ, xuất hiện lần đầu   |
| 2   | `001234567896` | `REJECTED / DUPLICATE_IN_BATCH` ❌ | Trùng CCCD trong cùng batch |
**Screenshot:**
<img width="1920" height="1080" alt="Screenshot (693)" src="https://github.com/user-attachments/assets/9f5be420-19c8-4355-be9f-fd3872b7a8b1" />
<img width="1920" height="1080" alt="Screenshot (694)" src="https://github.com/user-attachments/assets/e5123f20-01b7-4fc7-8707-52bde9aeba76" />

### Case 9 — ❌ CCCD không hợp lệ → INVALID_SOCCCD
**Mô tả:**
Kiểm tra validation đối với trường `socccd`. CCCD phải có đúng 12 chữ số. Record có CCCD không hợp lệ phải bị `REJECTED / INVALID_SOCCCD`, trong khi các record hợp lệ khác trong cùng batch vẫn được xử lý bình thường.
Case này kiểm tra khả năng xử lý **partial result trong một batch**, đảm bảo một record invalid không làm thất bại toàn bộ request.
**Expected:**
| STT | CCCD           | Kết quả                       | Lý do                       |
| --- | -------------- | ----------------------------- | --------------------------- |
| 1   | `12345`        | `REJECTED / INVALID_SOCCCD` ❌ | Không đủ 12 chữ số          |
| 2   | `001234567897` | `ACCEPTED` ✅                  | CCCD hợp lệ và chưa tồn tại |
**Screenshot:**
<img width="1920" height="1080" alt="Screenshot (695)" src="https://github.com/user-attachments/assets/36b933d7-70a0-488e-b925-49f80d9707ba" />
<img width="1920" height="1080" alt="Screenshot (696)" src="https://github.com/user-attachments/assets/99db248f-1534-4c6e-858d-fd889adf6c67" />

### Case 10 — ❌ Ngày sinh không hợp lệ → INVALID_DATA
**Mô tả:**
Kiểm tra validation đối với trường `ngaysinh`. Ngày sinh phải đúng định dạng `yyyy-MM-dd` và phải là ngày hợp lệ. Ví dụ `30/02/1990` không tồn tại nên record phải bị từ chối với `INVALID_DATA`.
Case này đồng thời kiểm tra rằng record hợp lệ khác trong cùng batch vẫn được xử lý.
**Expected:**

| STT | CCCD           | Kết quả                     | Lý do                           |
| --- | -------------- | --------------------------- | ------------------------------- |
| 1   | `001234567898` | `REJECTED / INVALID_DATA` ❌ | Ngày `30/02/1990` không tồn tại |
| 2   | `001234567899` | `ACCEPTED` ✅                | Ngày sinh hợp lệ                |

**Screenshot**
<img width="1920" height="1080" alt="Screenshot (697)" src="https://github.com/user-attachments/assets/3400bf55-7604-425e-a8e3-12711bfd7772" />
<img width="1920" height="1080" alt="Screenshot (698)" src="https://github.com/user-attachments/assets/8c7b6b9a-0961-46a1-be01-20a527e140b5" />

### Case 11 — ❌ `gioitinh` không hợp lệ → INVALID_DATA
**Mô tả:**
Kiểm tra validation đối với trường `gioitinh`. Theo rule, giá trị được phép là `"0"` hoặc `"1"`. Giá trị `"2"` không hợp lệ và phải bị từ chối với `INVALID_DATA`.
Case này đồng thời kiểm tra record hợp lệ trong cùng batch vẫn được xử lý độc lập.
**Điều kiện:**
* Sử dụng `batch_id` mới.
* `001234567900` có `gioitinh = "2"` không hợp lệ.
* `001234567901` có `gioitinh = "1"` hợp lệ và chưa tồn tại.
**Expected:**
| STT | CCCD           | Kết quả                     | Lý do                         |
| --- | -------------- | --------------------------- | ----------------------------- |
| 1   | `001234567900` | `REJECTED / INVALID_DATA` ❌ | `gioitinh = "2"` không hợp lệ |
| 2   | `001234567901` | `ACCEPTED` ✅                | `gioitinh = "1"` hợp lệ       |
**Screenshot:**
<img width="1920" height="1080" alt="Screenshot (699)" src="https://github.com/user-attachments/assets/10a62e41-9518-4ab0-b6ad-d405546a9972" />
<img width="1920" height="1080" alt="Screenshot (700)" src="https://github.com/user-attachments/assets/0ec33b8a-4467-4efd-97eb-139d28bcda68" />

### Case 12 — ❌ Thiếu `batch_id` → INVALID_REQUEST
**Mô tả:**
Kiểm tra validation ở cấp độ **toàn bộ request** khi trường bắt buộc `batch_id` không được truyền.
Khác với các case invalid record, đây không phải lỗi của một record riêng lẻ. Request phải bị từ chối ngay với `HTTP 400 / INVALID_REQUEST` và không được tạo batch hoặc ghi dữ liệu vào bảng chính.
**Screenshot:**
<img width="1920" height="1080" alt="Screenshot (701)" src="https://github.com/user-attachments/assets/951a8708-6eaf-4758-9378-c06f5e6d72e5" />


### Case 13 — Hai request đồng thời cùng CCCD → kiểm tra Race Condition
**Mô tả:**
Kiểm tra trường hợp hai request khác nhau được gửi gần như đồng thời nhưng cùng chứa một `socccd`.

Mục tiêu là kiểm tra tính nhất quán khi có concurrent request và xác nhận database constraint/business rule không cho phép tạo hai bản ghi cùng CCCD.
**Screenshot cần chụp:**
<img width="1920" height="1080" alt="Screenshot (702)" src="https://github.com/user-attachments/assets/d3de9793-6c59-4dac-8207-897efdd8d8d1" />
<img width="1920" height="1080" alt="Screenshot (703)" src="https://github.com/user-attachments/assets/0a5b0a22-b2fb-414d-99b0-f11739745e9a" />
<img width="1920" height="1080" alt="Screenshot (704)" src="https://github.com/user-attachments/assets/e00bf450-0018-4fd4-acfd-33f2ee76448b" />
