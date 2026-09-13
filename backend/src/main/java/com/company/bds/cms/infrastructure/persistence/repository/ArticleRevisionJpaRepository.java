package com.company.bds.cms.infrastructure.persistence.repository;

import com.company.bds.cms.domain.model.ArticleStatus;
import com.company.bds.cms.infrastructure.persistence.entity.ArticleRevisionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArticleRevisionJpaRepository extends JpaRepository<ArticleRevisionJpaEntity, UUID> {
    List<ArticleRevisionJpaEntity> findByArticleIdOrderByRevisionNumberDesc(UUID articleId, Pageable pageable);
    List<ArticleRevisionJpaEntity> findByStatus(ArticleStatus status, Pageable pageable);
}
