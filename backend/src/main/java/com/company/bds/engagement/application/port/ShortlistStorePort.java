package com.company.bds.engagement.application.port;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code shortlists}, {@code shortlist_members}, {@code shortlist_items}. */
public interface ShortlistStorePort {

    void create(UUID id, UUID ownerId, String name, Instant now);

    int countOwned(UUID ownerId);

    /** The list with the caller's access: OWNER, EDITOR, VIEWER; empty when the caller has none. */
    Optional<Access> access(UUID shortlistId, UUID userId);

    /** Lists the user owns or is a member of, most recently updated first. */
    List<Summary> listFor(UUID userId, int limit);

    /** Compare-and-set rename; false when {@code expectedVersion} is stale. */
    boolean rename(UUID shortlistId, String name, long expectedVersion, Instant now);

    void delete(UUID shortlistId);

    /** Replaces the share token (hash) and link role; null hash = sharing off. Bumps the version. */
    void setShare(UUID shortlistId, @Nullable String tokenHash, @Nullable String role, Instant now);

    Optional<Shared> findByTokenHash(String tokenHash);

    /** Adds a member; an existing member keeps the higher of the two roles. */
    void upsertMember(UUID shortlistId, UUID userId, String role, Instant now);

    boolean setMemberRole(UUID shortlistId, UUID userId, String role);

    boolean removeMember(UUID shortlistId, UUID userId);

    boolean setMuted(UUID shortlistId, UUID userId, boolean muted);

    List<Member> members(UUID shortlistId);

    boolean addItem(UUID shortlistId, UUID listingId, UUID addedBy, Instant now);

    boolean removeItem(UUID shortlistId, UUID listingId);

    int countItems(UUID shortlistId);

    /** Newest first. */
    List<Item> items(UUID shortlistId, int limit);

    void touch(UUID shortlistId, Instant now);

    /** Owner and members other than {@code exceptUserId} who have not muted the list. */
    List<UUID> audience(UUID shortlistId, UUID exceptUserId);

    record Access(UUID shortlistId, UUID ownerId, String name, String role, boolean muted, long version,
                  boolean shared, @Nullable String shareRole) {}

    record Summary(UUID id, String name, String role, int itemCount, int memberCount, boolean shared, boolean muted,
                   long version, Instant updatedAt) {}

    record Shared(UUID shortlistId, UUID ownerId, String ownerName, String name, String shareRole) {}

    record Member(UUID userId, String name, String role, boolean muted, Instant joinedAt) {}

    record Item(UUID listingId, UUID addedBy, Instant addedAt) {}
}
