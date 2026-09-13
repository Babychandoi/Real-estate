package com.company.bds.catalog.api;

import com.company.bds.catalog.api.request.CreateProjectRequest;
import com.company.bds.catalog.api.response.ProjectResponse;
import com.company.bds.catalog.application.ProjectCatalogService;
import com.company.bds.catalog.domain.model.Project;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/catalog/projects")
public class ProjectController {

    private final ProjectCatalogService projectService;

    public ProjectController(ProjectCatalogService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.createProject(
                request.getName(),
                request.getDeveloperName(),
                request.getProvinceCode(),
                request.getDistrictCode(),
                request.getAddress(),
                request.getTotalAreaM2(),
                request.getTotalBlocks(),
                request.getTotalUnits(),
                request.getHandoverYear(),
                request.getLegalLicenseNumber()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.fromDomain(project));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getProjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Project> list = projectService.searchProjects(keyword, Math.max(0, page), Math.max(1, Math.min(100, size)));
        return ResponseEntity.ok(list.stream().map(ProjectResponse::fromDomain).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable UUID id) {
        return projectService.getProjectById(id)
                .map(p -> ResponseEntity.ok(ProjectResponse.fromDomain(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProjectResponse> getProjectBySlug(@PathVariable String slug) {
        return projectService.getProjectBySlug(slug)
                .map(p -> ResponseEntity.ok(ProjectResponse.fromDomain(p)))
                .orElse(ResponseEntity.notFound().build());
    }
}
