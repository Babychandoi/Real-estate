package com.company.bds.seo.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Who operates the site (audit UI-15): legal entity details come from configuration ({@code APP_OPERATOR_*}), never
 * from code. An unset value is {@code null} and the pages say "Chưa có dữ liệu" instead of inventing one.
 */
@Component
public class SiteOperator {
    public record Info(String siteName, String legalName, String businessRegistration, String taxCode, String address,
                       String representative, String email, String phone, String hotlineHours, boolean complete) {}

    private final Info info;

    public SiteOperator(@Value("${app.operator.site-name:Nhà Đất Chuẩn}") String siteName,
                        @Value("${app.operator.legal-name:}") String legalName,
                        @Value("${app.operator.business-registration:}") String businessRegistration,
                        @Value("${app.operator.tax-code:}") String taxCode,
                        @Value("${app.operator.address:}") String address,
                        @Value("${app.operator.representative:}") String representative,
                        @Value("${app.operator.email:}") String email,
                        @Value("${app.operator.phone:}") String phone,
                        @Value("${app.operator.hotline-hours:}") String hotlineHours) {
        String legal = blank(legalName);
        String registration = blank(businessRegistration);
        String addr = blank(address);
        String mail = blank(email);
        this.info = new Info(blank(siteName) == null ? "Nhà Đất Chuẩn" : siteName.trim(), legal, registration, blank(taxCode), addr,
                blank(representative), mail, blank(phone), blank(hotlineHours),
                legal != null && registration != null && addr != null && mail != null);
    }

    public Info info() { return info; }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
