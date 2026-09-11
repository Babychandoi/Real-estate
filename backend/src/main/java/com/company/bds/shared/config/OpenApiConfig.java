package com.company.bds.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình Swagger / OpenAPI 3.0 theo tiêu chuẩn Waterfall BDS 2026.
 * Tra cứu tài liệu tương tác tại: http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hệ Thống Bất Động Sản Waterfall Enterprise 2026 - REST API Docs")
                        .description("Tài liệu đặc tả toàn diện 32 Yêu cầu Chức năng (FR01 - FR32) và 8 Ca Sử Dụng (UC01 - UC08) "
                                + "bao gồm: BĐS Listings, Thẩm định eKYC Chính chủ, Điều phối Lead & Chống lãng phí (Anti-Spam), "
                                + "Giao dịch Đặt cọc Escrow Vault, Danh mục Dự án & Căn hộ, Xuất bản CMS và Phễu chuyển đổi.")
                        .version("0.9.1")
                        .contact(new Contact()
                                .name("BDS Enterprise Engineering Team")
                                .email("engineering@company.com")
                                .url("https://company.com/bds"))
                        .license(new License()
                                .name("Commercial Proprietary License")
                                .url("https://company.com/licenses")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Môi trường Local Development"),
                        new Server().url("https://api.bds.company.com").description("Môi trường Staging / Production")
                ));
    }
}
