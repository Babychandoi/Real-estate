package com.company.bds.listing.infrastructure.persistence.mapper;

import com.company.bds.listing.domain.model.*;
import com.company.bds.listing.infrastructure.persistence.entity.ListingJpaEntity;
import com.company.bds.listing.infrastructure.persistence.entity.ListingMediaJpaEntity;
import com.company.bds.listing.infrastructure.persistence.entity.ListingRevisionJpaEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper chuyển đổi giữa Domain Model và JPA Entity.
 * Không truy cập CSDL, chuyển đổi tường minh.
 */
@Component
public class ListingEntityMapper {

    public Listing toDomain(ListingJpaEntity entity) {
        if (entity == null) return null;

        List<ListingRevision> revisions = new ArrayList<>();
        if (entity.getRevisions() != null) {
            for (ListingRevisionJpaEntity revEntity : entity.getRevisions()) {
                revisions.add(toRevisionDomain(revEntity));
            }
        }

        return new Listing(
                entity.getId(),
                entity.getOwnerId(),
                entity.getPublicRevisionId(),
                entity.getSlug(),
                ListingStatus.valueOf(entity.getStatus()),
                entity.isVerifiedOwner(),
                revisions,
                entity.getVersion() != null ? entity.getVersion() : 0L,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public ListingRevision toRevisionDomain(ListingRevisionJpaEntity revEntity) {
        List<ListingMedia> mediaList = new ArrayList<>();
        try {
            if (revEntity.getMediaList() != null) {
                for (ListingMediaJpaEntity mediaEntity : revEntity.getMediaList()) {
                    mediaList.add(new ListingMedia(
                            mediaEntity.getId(),
                            mediaEntity.getMediaUrl(),
                            mediaEntity.isPrimary(),
                            mediaEntity.getSortOrder()
                    ));
                }
            }
        } catch (org.hibernate.LazyInitializationException ignored) {
            // Không trong active persistence context hoặc media chưa fetch
        }

        return new ListingRevision(
                revEntity.getId(),
                revEntity.getListing() != null ? revEntity.getListing().getId() : null,
                revEntity.getRevisionNumber(),
                RevisionStatus.valueOf(revEntity.getStatus()),
                revEntity.getTitle(),
                ListingPurpose.valueOf(revEntity.getPurpose()),
                PropertyType.valueOf(revEntity.getPropertyType()),
                revEntity.getPriceVnd(),
                revEntity.getAreaM2(),
                revEntity.getBedrooms(), revEntity.getBathrooms(), revEntity.getFloors(),
                revEntity.getFrontageM(), revEntity.getRoadWidthM(), revEntity.getDirection(), revEntity.getLegalStatus(),
                revEntity.getDescription(),
                revEntity.getProvinceCode(),
                revEntity.getDistrictCode(),
                revEntity.getWardCode(),
                revEntity.getAddressSummary(),
                revEntity.getPublicLatitude(),
                revEntity.getPublicLongitude(),
                revEntity.getPublicLatitude(),
                revEntity.getPublicLongitude(),
                mediaList,
                revEntity.getCreatedAt(),
                revEntity.getSubmittedAt(),
                revEntity.getModeratedAt(),
                revEntity.getModerationNote()
        );
    }

    public ListingJpaEntity toJpaEntity(Listing domain) {
        if (domain == null) return null;

        ListingJpaEntity entity = new ListingJpaEntity(
                domain.getId(),
                domain.getOwnerId(),
                domain.getPublicRevisionId(),
                domain.getSlug(),
                domain.getStatus().name(),
                domain.getVersion(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
        entity.setVerifiedOwner(domain.isVerifiedOwner());

        if (domain.getRevisions() != null) {
            for (ListingRevision revDomain : domain.getRevisions()) {
                ListingRevisionJpaEntity revEntity = toRevisionJpaEntity(revDomain, entity);
                entity.addRevision(revEntity);
            }
        }

        return entity;
    }

    public ListingRevisionJpaEntity toRevisionJpaEntity(ListingRevision revDomain, ListingJpaEntity parentListing) {
        ListingRevisionJpaEntity revEntity = new ListingRevisionJpaEntity();
        revEntity.setId(revDomain.getId());
        revEntity.setListing(parentListing);
        revEntity.setRevisionNumber(revDomain.getRevisionNumber());
        revEntity.setStatus(revDomain.getStatus().name());
        revEntity.setTitle(revDomain.getTitle());
        revEntity.setPurpose(revDomain.getPurpose().name());
        revEntity.setPropertyType(revDomain.getPropertyType().name());
        revEntity.setPriceVnd(revDomain.getPriceVnd());
        revEntity.setAreaM2(revDomain.getAreaM2());
        revEntity.setBedrooms(revDomain.getBedrooms());
        revEntity.setBathrooms(revDomain.getBathrooms());
        revEntity.setFloors(revDomain.getFloors());
        revEntity.setFrontageM(revDomain.getFrontageM());
        revEntity.setRoadWidthM(revDomain.getRoadWidthM());
        revEntity.setDirection(revDomain.getDirection());
        revEntity.setLegalStatus(revDomain.getLegalStatus());
        revEntity.setDescription(revDomain.getDescription());
        revEntity.setProvinceCode(revDomain.getProvinceCode());
        revEntity.setDistrictCode(revDomain.getDistrictCode());
        revEntity.setWardCode(revDomain.getWardCode());
        revEntity.setAddressSummary(revDomain.getAddressSummary());
        revEntity.setPublicLatitude(revDomain.getPublicLatitude());
        revEntity.setPublicLongitude(revDomain.getPublicLongitude());
        revEntity.setCreatedAt(revDomain.getCreatedAt());
        revEntity.setSubmittedAt(revDomain.getSubmittedAt());
        revEntity.setModeratedAt(revDomain.getModeratedAt());
        revEntity.setModerationNote(revDomain.getModerationNote());

        if (revDomain.getMediaList() != null) {
            for (ListingMedia mediaDomain : revDomain.getMediaList()) {
                ListingMediaJpaEntity mediaEntity = new ListingMediaJpaEntity(
                        mediaDomain.id(),
                        revEntity,
                        mediaDomain.mediaUrl(),
                        mediaDomain.isPrimary(),
                        mediaDomain.sortOrder(),
                        revDomain.getCreatedAt()
                );
                revEntity.addMedia(mediaEntity);
            }
        }

        return revEntity;
    }
}
