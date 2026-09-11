package com.company.bds.catalog.infrastructure.persistence.repository;

import com.company.bds.catalog.infrastructure.persistence.entity.ProjectJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, UUID> {
    Optional<ProjectJpaEntity> findBySlug(String slug);
    List<ProjectJpaEntity> findByDistrictCode(String districtCode);
    List<ProjectJpaEntity> findByNameContainingIgnoreCase(String name);
}
