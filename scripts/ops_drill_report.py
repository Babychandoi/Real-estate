#!/usr/bin/env python3
"""Markdown summary of scripts/ci-ops-drill.sh results (key=value lines). Derives RPO from the marker rows:
rows lost = last sequence committed before the loss - last sequence present after the restore; data-loss window =
commit time of the last row before the loss - commit time of the last restored row."""
import sys
from datetime import datetime


def load(path):
    values = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            if "=" in line:
                key, value = line.rstrip("\n").split("=", 1)
                values[key] = value
    return values


def marker(value):
    seq, _, stamp = (value or "").partition(" ")
    if not seq or not stamp:
        return None, None
    return int(seq), datetime.strptime(stamp, "%Y-%m-%dT%H:%M:%S.%fZ")


def rpo(values, key):
    lost_seq, lost_at = marker(values.get("last_marker_at_loss"))
    got_seq, got_at = marker(values.get(key))
    if lost_seq is None or got_seq is None:
        return "n/a", "n/a"
    return str(lost_seq - got_seq), f"{(lost_at - got_at).total_seconds():.1f}"


def row(label, value):
    return f"| {label} | {value} |"


def recovery(values):
    pitr_rows, pitr_window = rpo(values, "pitr_restored_last_marker")
    host_rows, host_window = rpo(values, "hostloss_restored_last_marker")
    out = [
        "# Recovery drill (CI)",
        "",
        "| Measure | Value |",
        "|---|---|",
        row("pg_dump set / media set / base backup", f"`{values.get('db_set')}` / `{values.get('media_set')}` / `{values.get('base_set')}`"),
        row("pg_dump duration, size, rows", f"{values.get('db_backup_ms')} ms, {values.get('db_backup_bytes')} B, {values.get('db_backup_rows')} rows"),
        row("Media objects in the set", values.get("media_backup_objects")),
        row("WAL segments shipped before the loss", values.get("wal_segments_shipped")),
        row("Counts at loss (listings users media markers)", values.get("counts_at_loss")),
        row("**B. DB volume lost, PITR**: data restore", f"{values.get('pitr_data_restore_seconds')} s"),
        row("B: RTO to first page / to serving (search on ES)", f"{values.get('pitr_rto_first_page_seconds')} s / **{values.get('pitr_rto_seconds')} s**"),
        row("B: RPO rows lost / data-loss window", f"{pitr_rows} rows / **{pitr_window} s**"),
        row("B: user registered after the backup present", values.get("pitr_user_after_backup")),
        row("**A. Host lost, pg_dump + media**: DB restore / media restore", f"{values.get('hostloss_db_restore_seconds')} s / {values.get('hostloss_media_restore_seconds')} s"),
        row("A: RTO to first page / to serving (search on ES)", f"{values.get('hostloss_rto_first_page_seconds')} s / **{values.get('hostloss_rto_seconds')} s**"),
        row("A: RPO rows lost / data-loss window", f"{host_rows} rows / **{host_window} s**"),
        row("A: user registered after the backup present", values.get("hostloss_user_after_backup")),
        row("A: media_objects rows vs restored objects", values.get("hostloss_media_references")),
        row("restore-drill.sh (verified row counts + sha256)", values.get("restore_drill")),
        row("Cold start of the stack (first `up --wait`)", f"{values.get('cold_start_seconds')} s"),
        row("Image build", f"{values.get('build_seconds')} s"),
    ]
    return "\n".join(out)


def rollback(values):
    out = ["# Rollback, IP chain and restart drill (CI)", "", "| Key | Value |", "|---|---|"]
    out += [row(f"`{key}`", value) for key, value in values.items()]
    return "\n".join(out)


if __name__ == "__main__":
    kind, path = sys.argv[1], sys.argv[2]
    data = load(path)
    print(recovery(data) if kind == "recovery" else rollback(data))
