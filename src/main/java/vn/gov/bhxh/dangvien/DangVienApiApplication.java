package vn.gov.bhxh.dangvien;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import vn.gov.bhxh.dangvien.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DangVienApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DangVienApiApplication.class, args);
    }
}
