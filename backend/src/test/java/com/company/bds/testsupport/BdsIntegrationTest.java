package com.company.bds.testsupport;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full application context on PostgreSQL/PostGIS + Flyway ({@code ddl-auto: validate}), MockMvc, the {@code test}
 * profile and the shared test infrastructure (Redis DB {@code BDS_TEST_REDIS_DB} (default 1), Mailpit SMTP, Elasticsearch
 * disabled unless {@code bds.test.elasticsearch=true}). All contexts of one JVM share one fresh database, so tests must
 * create their own rows (see {@link TestData}) and never assume the database is empty: Flyway seed rows exist.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = BdsIntegrationTestInitializer.class)
@Import(BdsTestConfiguration.class)
public @interface BdsIntegrationTest {
    /** Extra inlined properties; each distinct set creates (and caches) its own application context. */
    @AliasFor(annotation = SpringBootTest.class, attribute = "properties")
    String[] properties() default {};
}
