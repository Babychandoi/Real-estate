package com.company.bds.media;

import com.company.bds.shared.security.CurrentUser;
import com.company.bds.iam.application.AuthService;
import com.company.bds.verification.application.KycDocumentAccessService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "app.media.storage-enabled", havingValue = "true")
public class MediaController {
    private static final String OBJECT_KEY = "[0-9a-fA-F-]{36}\\.(?:jpg|png|webp|avif)";
    private static final String ANY_KEY = MediaKeys.ANY_KEY_REGEX;
    /** Public media may be taken down (hidden listing, banned seller): shared caches must drop it within a day. */
    static final CacheControl PUBLIC_MEDIA_CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();
    private final MediaStorageService storage;
    private final AuthService authService;
    private final KycDocumentAccessService kycAccess;

    public MediaController(MediaStorageService storage, AuthService authService, KycDocumentAccessService kycAccess) {
        this.storage = storage; this.authService = authService; this.kycAccess = kycAccess;
    }

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
        // Owners re-confirm their password; staff additionally need a logged, reasoned access to this owner's documents.
        boolean allowed = privileged
                ? kycAccess.staffMayRead(CurrentUser.id(authentication), accessToken, objectKey)
                : authService.hasKycDocumentAccess(CurrentUser.id(authentication), accessToken);
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(privileged
                    ? "Cần nêu lý do và xác nhận mật khẩu trước khi xem giấy tờ định danh."
                    : "Cần xác nhận lại mật khẩu để xem ảnh định danh.");
        }
        MediaStorageService.StoredImage image=storage.readPrivate(CurrentUser.id(authentication),privileged,objectKey);
        StreamingResponseBody body=output->{try(var input=image.stream()){input.transferTo(output);}};
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).cacheControl(CacheControl.noStore()).body(body);
    }

    /** Public originals and WebP variants, only while publicly referenced (see {@link MediaStorageService}). */
    @GetMapping("/public/media/{objectKey:" + ANY_KEY + "}")
    public ResponseEntity<StreamingResponseBody> read(@PathVariable String objectKey) {
        return stream(storage.read(objectKey), PUBLIC_MEDIA_CACHE);
    }

    /** Capability URL issued to owners/staff (contract §10); anonymous and never cached (Referrer-Policy no-referrer is global). */
    @GetMapping("/media/signed/{objectKey:" + ANY_KEY + "}")
    public ResponseEntity<StreamingResponseBody> readSigned(@PathVariable String objectKey,
                                                            @RequestParam(name = "exp", defaultValue = "0") long exp,
                                                            @RequestParam(name = "sig", defaultValue = "") String sig) {
        return stream(storage.readSigned(objectKey, exp, sig), CacheControl.noStore());
    }

    /** Signed URLs for images the caller owns (or any listing image for staff), e.g. drafts and hidden listings. */
    @PostMapping(path = "/media/signed-urls", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MediaStorageService.SignedUrls> sign(@RequestBody SignRequest request, Authentication authentication) {
        boolean staff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(storage.signForViewer(CurrentUser.id(authentication), staff, request.urls()));
    }

    public record SignRequest(List<String> urls) {}

    @ExceptionHandler(MediaStorageService.MediaNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(MediaStorageService.MediaNotFoundException ex) {
        return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).body(Map.of("message", ex.getMessage()));
    }

    private static ResponseEntity<StreamingResponseBody> stream(MediaStorageService.StoredImage image, CacheControl cache) {
        StreamingResponseBody body = output -> {
            try (var input = image.stream()) { input.transferTo(output); }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.sizeBytes())
                .cacheControl(cache)
                .body(body);
    }

    @DeleteMapping("/media/images/{objectKey:" + OBJECT_KEY + "}")
    public ResponseEntity<Map<String, Boolean>> delete(@PathVariable String objectKey, Authentication authentication) {
        storage.delete(CurrentUser.id(authentication), objectKey);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
