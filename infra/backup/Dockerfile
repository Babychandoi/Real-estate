# syntax=docker/dockerfile:1.7
# Encrypted backup / restore tool for Nhà Đất Chuẩn (audit F21.2): PostgreSQL 16 client, MinIO client, age.
# Used by infra/compose.backup.yaml (scheduled backups) and scripts/restore-drill.sh (restore drills).
FROM alpine:3.22
RUN apk add --no-cache bash coreutils findutils jq zstd tzdata age minio-client postgresql16-client \
 && addgroup -S -g 10001 backup \
 && adduser -S -D -H -u 10001 -G backup -h /tmp backup \
 && mkdir -p /backups \
 && chown 10001:10001 /backups
COPY --chmod=0755 lib.sh backup.sh restore.sh /usr/local/bin/
ENV BACKUP_ROOT=/backups \
    HOME=/tmp \
    MC_CONFIG_DIR=/tmp/.mcli
USER 10001:10001
ENTRYPOINT ["/usr/local/bin/backup.sh"]
CMD ["schedule"]
