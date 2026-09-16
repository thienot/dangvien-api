package vn.gov.bhxh.dangvien.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 1 phan tu trong data[].
 * Khong dung Bean Validation (@NotBlank...) o day vi loi cua tung ban ghi
 * phai tra ve dang REJECTED/error_code trong results[] (muc 2.2),
 * chu khong phai loi 400 cap toan bo request.
 */
@Getter
@Setter
public class DangVienItemRequest {

    private String socccd;
    private String hoten;
    private String ngaysinh;
    private String gioitinh;
}
