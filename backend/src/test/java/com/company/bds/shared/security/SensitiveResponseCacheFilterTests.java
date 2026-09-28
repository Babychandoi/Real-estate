package com.company.bds.shared.security;

import com.company.bds.iam.application.AuthService;
import com.company.bds.media.MediaController;
import com.company.bds.media.MediaStorageService;
import io.minio.GetObjectResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Audit F10.2: no-store is enforced for private areas even against the controller; public media keeps its cache. */
class SensitiveResponseCacheFilterTests {
    private final SensitiveResponseCacheFilter filter = new SensitiveResponseCacheFilter();

    @Test
    void privateAreasArePrefixMatchedAndThePublicCatalogueIsExcluded() {
        for (String path : List.of("/api/v1/kyc/queue", "/api/v1/leads", "/api/v1/leads/12/contact", "/api/v1/public/leads",
                "/api/v1/billing/orders", "/api/v1/admin/users", "/api/v1/moderation/queue", "/api/v1/auth/login",
                "/api/v1/media/kyc/0f6f0b8e-1c2d-4e5f-8a9b-0c1d2e3f4a5b.jpg", "/api/v1/listings/abc/draft",
                "/API/V1/KYC//queue")) {
            assertThat(filter.isSensitive(path)).as(path).isTrue();
        }
        for (String path : List.of("/api/v1/billing/plans", "/api/v1/public/media/0f6f0b8e-1c2d-4e5f-8a9b-0c1d2e3f4a5b.jpg",
                "/api/v1/listings/search", "/api/v1/public/articles", "/api/v1/leadsx")) {
            assertThat(filter.isSensitive(path)).as(path).isFalse();
        }
    }

    @Test
    void percentEncodedPrivatePathsAreStillRecognised() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/%6Byc/queue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Cache-Control")).isEqualTo(SensitiveResponseCacheFilter.NO_STORE);
    }

    @Test
    void controllerCannotOptAPrivateResponseIntoSharedCaching() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/leads/sent");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpServlet careless = new HttpServlet() {
            @Override protected void service(HttpServletRequest req, HttpServletResponse res) {
                res.setHeader("Cache-Control", "public, max-age=600");
                res.addHeader("Cache-Control", "s-maxage=600");
                res.setDateHeader("Expires", System.currentTimeMillis() + 600_000);
                res.setStatus(200);
            }
        };

        filter.doFilter(request, response, new MockFilterChain(careless));

        assertThat(response.getHeaders("Cache-Control")).containsExactly(SensitiveResponseCacheFilter.NO_STORE);
        assertThat(response.getHeader("Expires")).isEqualTo("0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    void publicMediaKeepsImmutableCachingWhilePrivateKycImagesAreNotStored() throws Exception {
        MediaStorageService storage = mock(MediaStorageService.class);
        when(storage.read(anyString())).thenReturn(new MediaStorageService.StoredImage(mock(GetObjectResponse.class), "image/jpeg", 3));
        when(storage.readPrivate(any(UUID.class), anyBoolean(), anyString()))
                .thenReturn(new MediaStorageService.StoredImage(mock(GetObjectResponse.class), "image/jpeg", 3));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(storage, mock(AuthService.class)))
                .addFilters(filter).build();
        String key = UUID.randomUUID() + ".jpg";

        MvcResult publicImage = mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/public/media/" + key)).andReturn())).andReturn();
        assertThat(publicImage.getResponse().getHeader("Cache-Control")).contains("max-age=31536000").contains("public").contains("immutable");

        UsernamePasswordAuthenticationToken moderator = UsernamePasswordAuthenticationToken.authenticated(
                UUID.randomUUID().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_MODERATOR")));
        MvcResult kycImage = mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/media/kyc/" + key).principal(moderator)).andReturn())).andReturn();
        assertThat(kycImage.getResponse().getStatus()).isEqualTo(200);
        assertThat(kycImage.getResponse().getHeaders("Cache-Control")).containsExactly(SensitiveResponseCacheFilter.NO_STORE);
    }
}
