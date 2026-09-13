package com.company.bds.catalog.application;

import com.company.bds.catalog.domain.model.Project;
import com.company.bds.catalog.infrastructure.persistence.port.ProjectPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class ProjectCatalogService {

    private final ProjectPersistencePort persistencePort;

    public ProjectCatalogService(ProjectPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    public Project createProject(
            String name,
            String developerName,
            String provinceCode,
            String districtCode,
            String address,
            BigDecimal totalAreaM2,
            int totalBlocks,
            int totalUnits,
            Integer handoverYear,
            String legalLicenseNumber
    ) {
        String baseSlug = toSlug(name);
        String slug = baseSlug;
        int count = 1;
        while (persistencePort.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + count++;
        }

        Project project = Project.create(
                name,
                slug,
                developerName,
                provinceCode,
                districtCode,
                address,
                totalAreaM2,
                totalBlocks,
                totalUnits,
                handoverYear,
                legalLicenseNumber
        );

        return persistencePort.save(project);
    }

    @Transactional(readOnly = true)
    public List<Project> getAllProjects(int page, int size) {
        return persistencePort.findPage(page, size);
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProjectById(UUID id) {
        return persistencePort.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProjectBySlug(String slug) {
        return persistencePort.findBySlug(slug);
    }

    @Transactional(readOnly = true)
    public List<Project> searchProjects(String keyword, int page, int size) {
        if (keyword == null || keyword.isBlank()) {
            return persistencePort.findPage(page, size);
        }
        return persistencePort.searchByName(keyword.trim(), page, size);
    }

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private String toSlug(String input) {
        if (input == null) return "du-an";
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("").toLowerCase(Locale.ENGLISH);
        slug = slug.replaceAll("[-]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "du-an-" + System.currentTimeMillis() : slug;
    }
}
