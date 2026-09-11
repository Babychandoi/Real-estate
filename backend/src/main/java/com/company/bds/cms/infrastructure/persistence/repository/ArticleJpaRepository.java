package com.company.bds.cms.infrastructure.persistence.repository;

import com.company.bds.cms.domain.model.ArticleCategory;
import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.cms.infrastructure.persistence.entity.ArticleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArticleJpaRepository extends JpaRepository<ArticleJpaEntity, UUID> {
    Optional<ArticleJpaEntity> findBySlug(String slug);
    List<ArticleJpaEntity> findByCategory(ArticleCategory category);
    List<ArticleJpaEntity> findByStatus(ArticleStatus status);
}
