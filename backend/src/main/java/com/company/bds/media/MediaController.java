package com.company.bds.media;

import com.company.bds.shared.security.CurrentUser;
import com.company.bds.iam.application.AuthService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "app.media.storage-enabled", havingValue = "true")
public class MediaController {
    private static final String OBJECT_KEY = "[0-9a-fA-F-]{36}\\.(?:jpg|png|webp|avif)";
    private final MediaStorageService storage;
    private final AuthService authService;

    public MediaController(MediaStorageService storage, AuthService authService) { this.storage = storage; this.authService = authService; }

    @PostMapping(path = "/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaStorageService.UploadedImage> upload(
            @RequestPart("file") MultipartFile file, Authentication authentication) {
        return ResponseEntity.status(201).body(storage.upload(CurrentUser.id(authentication), file));
    }

    @PostMapping(path = "/media/kyc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaStorageService.UploadedImage> uploadKyc(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return ResponseEntity.status(201).body(storage.uploadKyc(CurrentUser.id(authentication), file));
    }

    @GetMapping("/media/kyc/{objectKey:" + OBJECT_KEY + "}")
    public ResponseEntity<StreamingResponseBody> readKyc(@PathVariable String objectKey,
                                                           @RequestHeader(value = "X-Kyc-Document-Access", required = false) String accessToken,
                                                           Authentication authentication) {
        boolean privileged=authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")||a.getAuthority().equals("ROLE_MODERATOR"));
        if (!privileged && !authService.hasKycDocumentAccess(CurrentUser.id(authentication), accessToken)) {
            throw new org.springframework.security.access.AccessDeniedException("Cần xác nhận lại mật khẩu để xem ảnh định danh.");
        }
        MediaStorageService.StoredImage image=storage.readPrivate(CurrentUser.id(authentication),privileged,objectKey);
        StreamingResponseBody body=output->{try(var input=image.stream()){input.transferTo(output);}};
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/public/media/{objectKey:" + OBJECT_KEY + "}")
    public ResponseEntity<StreamingResponseBody> read(@PathVariable String objectKey) {
        MediaStorageService.StoredImage image = storage.read(objectKey);
        StreamingResponseBody body = output -> {
            try (var input = image.stream()) { input.transferTo(output); }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.sizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(body);
    }

    @DeleteMapping("/media/images/{objectKey:" + OBJECT_KEY + "}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable String objectKey, Authentication authentication) {
        storage.delete(CurrentUser.id(authentication), objectKey);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
