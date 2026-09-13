package com.company.bds.catalog.infrastructure.persistence.adapter;

import com.company.bds.catalog.domain.model.Project;
import com.company.bds.catalog.infrastructure.persistence.entity.ProjectJpaEntity;
import com.company.bds.catalog.infrastructure.persistence.port.ProjectPersistencePort;
import com.company.bds.catalog.infrastructure.persistence.repository.ProjectJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProjectPersistenceAdapter implements ProjectPersistencePort {

    private final ProjectJpaRepository repository;

    public ProjectPersistenceAdapter(ProjectJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Project save(Project project) {
        ProjectJpaEntity entity = toEntity(project);
        ProjectJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Project> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Project> findBySlug(String slug) {
        return repository.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public List<Project> findPage(int page, int size) {
        return repository.findAll(PageRequest.of(page, size)).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Project> findByDistrict(String districtCode, int page, int size) {
        return repository.findByDistrictCode(districtCode, PageRequest.of(page, size)).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Project> searchByName(String keyword, int page, int size) {
        return repository.findByNameContainingIgnoreCase(keyword, PageRequest.of(page, size)).stream().map(this::toDomain).collect(Collectors.toList());
    }

    private ProjectJpaEntity toEntity(Project domain) {
        ProjectJpaEntity entity = new ProjectJpaEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setSlug(domain.getSlug());
        entity.setDeveloperName(domain.getDeveloperName());
        entity.setProvinceCode(domain.getProvinceCode());
        entity.setDistrictCode(domain.getDistrictCode());
        entity.setAddress(domain.getAddress());
        entity.setTotalAreaM2(domain.getTotalAreaM2());
        entity.setTotalBlocks(domain.getTotalBlocks());
        entity.setTotalUnits(domain.getTotalUnits());
        entity.setHandoverYear(domain.getHandoverYear());
        entity.setLegalLicenseNumber(domain.getLegalLicenseNumber());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private Project toDomain(ProjectJpaEntity entity) {
        return new Project(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDeveloperName(),
                entity.getProvinceCode(),
                entity.getDistrictCode(),
                entity.getAddress(),
                entity.getTotalAreaM2(),
                entity.getTotalBlocks(),
                entity.getTotalUnits(),
                entity.getHandoverYear(),
                entity.getLegalLicenseNumber(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
