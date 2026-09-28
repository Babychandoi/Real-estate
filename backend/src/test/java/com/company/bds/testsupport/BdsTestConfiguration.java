package com.company.bds.testsupport;

import com.company.bds.shared.security.PiiProtectionService;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** Beans shared by every {@link BdsIntegrationTest}: query counting and test-data builders. */
@TestConfiguration(proxyBeanMethods = false)
public class BdsTestConfiguration {

    /** Wraps the application DataSource so {@link QueryCount} can count the statements a code path executes. */
    @Bean
    static BeanPostProcessor queryCountingDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
                    return ProxyDataSourceBuilder.create(dataSource).name(QueryCount.DATA_SOURCE_NAME).countQuery().build();
                }
                return bean;
            }
        };
    }

    @Bean
    TestData testData(JdbcTemplate jdbc, PiiProtectionService pii) {
        return new TestData(jdbc, pii);
    }
}
