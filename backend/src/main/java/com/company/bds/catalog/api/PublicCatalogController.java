package com.company.bds.catalog.api;

import com.company.bds.catalog.application.PublicCatalogService;
import com.company.bds.catalog.application.PublicCatalogService.AreaList;
import com.company.bds.catalog.application.PublicCatalogService.AreaPage;
import com.company.bds.catalog.application.PublicCatalogService.Home;
import com.company.bds.catalog.application.PublicCatalogService.ProfileInput;
import com.company.bds.catalog.application.PublicCatalogService.ProjectList;
import com.company.bds.catalog.application.PublicCatalogService.ProjectPage;
import com.company.bds.catalog.application.port.PublicCatalogStore.Amenity;
import com.company.bds.catalog.application.port.PublicCatalogStore.ProjectRow;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Public project/area/home data (GET {@code /api/v2/public/**} is open) and the staff endpoint for a project's public
 * profile ({@code /api/v1/catalog/**} is ADMIN/MODERATOR and no-store).
 */
@RestController
public class PublicCatalogController {
    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final PublicCatalogService service;

    public PublicCatalogController(PublicCatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/v2/public/projects")
    public ResponseEntity<ProjectList> projects(@RequestParam(required = false) String district,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(service.projects(district, page, size));
    }

    @GetMapping("/api/v2/public/projects/{slug}")
    public ResponseEntity<ProjectPage> project(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(service.project(slug));
    }

    @GetMapping("/api/v2/public/areas")
    public ResponseEntity<AreaList> areas() {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(service.areas());
    }

    @GetMapping("/api/v2/public/areas/{slug}")
    public ResponseEntity<AreaPage> area(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(service.area(slug));
    }

    @GetMapping("/api/v2/public/home")
    public ResponseEntity<Home> home() {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(service.home());
    }

    public record PublicProfileResponse(ProjectRow project, List<Amenity> amenities) {}

    @GetMapping("/api/v1/catalog/projects/{id}/public-profile")
    public PublicProfileResponse profile(@PathVariable UUID id) {
        return new PublicProfileResponse(service.updatePublicProfileView(id), service.amenities(id));
    }

    @PutMapping("/api/v1/catalog/projects/{id}/public-profile")
    public PublicProfileResponse updateProfile(@PathVariable UUID id, @RequestBody ProfileInput input) {
        ProjectRow project = service.updatePublicProfile(id, input);
        return new PublicProfileResponse(project, service.amenities(id));
    }
}
