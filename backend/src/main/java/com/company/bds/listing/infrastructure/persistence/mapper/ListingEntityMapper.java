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
        ).withLifecycle(ListingSource.valueOf(entity.getSource()), entity.getAvailabilityConfirmedAt(),
                entity.getExpiresAt(), entity.getSoldCheckDueAt());
    }

    public ListingRevision toRevisionDomain(ListingRevisionJpaEntity revEntity) {
        // Media are loaded in batches (@BatchSize) inside the adapter's transaction. Mapping outside a transaction is a
        // bug and must fail loudly (F07.3): a swallowed LazyInitializationException used to return listings without images.
        List<ListingMedia> mediaList = new ArrayList<>();
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
        ).withAttributes(new ListingAttributes(
                revEntity.getMonthlyServiceFeeVnd(),
                revEntity.getDepositVnd(),
                revEntity.getFurnishing() == null ? null : Furnishing.valueOf(revEntity.getFurnishing()),
                revEntity.getLegalStatusCode() == null ? null : LegalStatusCode.valueOf(revEntity.getLegalStatusCode()),
                revEntity.getProjectId()));
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
        entity.setSource(domain.getSource().name());
        entity.setAvailabilityConfirmedAt(domain.getAvailabilityConfirmedAt());
        entity.setExpiresAt(domain.getExpiresAt());
        entity.setSoldCheckDueAt(domain.getSoldCheckDueAt());

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
        ListingAttributes attributes = revDomain.getAttributes();
        revEntity.setMonthlyServiceFeeVnd(attributes.monthlyServiceFeeVnd());
        revEntity.setDepositVnd(attributes.depositVnd());
        revEntity.setFurnishing(attributes.furnishing() == null ? null : attributes.furnishing().name());
        revEntity.setLegalStatusCode(attributes.legalStatusCode() == null ? null : attributes.legalStatusCode().name());
        revEntity.setProjectId(attributes.projectId());

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
