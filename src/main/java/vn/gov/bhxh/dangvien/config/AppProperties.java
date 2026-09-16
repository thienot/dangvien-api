package vn.gov.bhxh.dangvien.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.dangvien")
@Getter
@Setter
public class AppProperties {

    /** Token TINH do BHXH cap rieng cho VPTWD (Authorization: Bearer {token}) */
    private String token;

    /** So ban ghi toi da trong data[] moi lan goi, mac dinh 1000 */
    private int maxRecords = 1000;
}
