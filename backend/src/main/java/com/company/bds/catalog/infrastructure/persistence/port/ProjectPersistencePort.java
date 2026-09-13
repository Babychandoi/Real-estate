package com.company.bds.catalog.infrastructure.persistence.port;

import com.company.bds.catalog.domain.model.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectPersistencePort {
    Project save(Project project);
    Optional<Project> findById(UUID id);
    Optional<Project> findBySlug(String slug);
    List<Project> findPage(int page, int size);
    List<Project> findByDistrict(String districtCode, int page, int size);
    List<Project> searchByName(String keyword, int page, int size);
}
