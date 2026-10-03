#!/usr/bin/env python3
"""Markdown summary of scripts/ci-ops-drill.sh results (key=value lines). Derives RPO from the marker rows:
rows lost = last sequence committed before the loss - last sequence present after the restore; data-loss window =
commit time of the last row before the loss - commit time of the last restored row."""
import math
import re
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
    try:
        lost_seq, lost_at = marker(values.get("last_marker_at_loss"))
        got_seq, got_at = marker(values.get(key))
    except ValueError:
        return "invalid", "invalid"
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


def validate(kind, values):
    """Require recorded success, rather than treating an emitted report as success."""
    errors = []

    def require(key, expected):
        if values.get(key) != expected:
            errors.append(f"{key}: expected {expected}, got {values.get(key, 'missing')!r}")

    def seconds(key):
        try:
            value = float(values[key])
            if not math.isfinite(value) or value < 0:
                raise ValueError
            return value
        except (KeyError, ValueError):
            errors.append(f"{key}: missing or invalid elapsed seconds ({values.get(key, 'missing')!r})")
            return None

    def serving(label, first_suffix, serving_suffix):
        first = seconds(f"{label}_{first_suffix}")
        ready = seconds(f"{label}_{serving_suffix}")
        if first is not None and ready is not None and first > ready:
            errors.append(f"{label}: first page occurred after serving")

    for key, value in values.items():
        if re.match(r"FAIL(?:\b|$)", value):
            errors.append(f"{key}: {value}")

    if kind == "recovery":
        require("restore_drill", "PASS")
        require("hostloss_media_references", "PASS")
        for key in ("pitr_data_restore_seconds", "hostloss_db_restore_seconds", "hostloss_media_restore_seconds"):
            seconds(key)
        for label in ("pitr", "hostloss"):
            serving(label, "rto_first_page_seconds", "rto_seconds")
        for key in ("last_marker_at_loss", "pitr_restored_last_marker", "hostloss_restored_last_marker"):
            try:
                seq, stamp = marker(values.get(key))
                if seq is None or stamp is None or seq < 0:
                    raise ValueError
            except ValueError:
                errors.append(f"{key}: missing or invalid RPO marker")
    elif kind == "rollback":
        # The drill records resolved SHAs, even when PREVIOUS_REFS contains symbolic refs.
        refs = values.get("previous_refs", "").split()
        if not refs or any(not re.fullmatch(r"[0-9a-f]{4,40}", ref) for ref in refs):
            errors.append("previous_refs: missing or invalid resolved commit IDs")
        labels = ["current", "after_restarts"]
        for ref in dict.fromkeys(refs):
            for label in (f"rollback_{ref}", f"forward_after_{ref}"):
                require(f"{label}_start", "PASS")
                seconds(f"{label}_healthy_seconds")
                labels.append(label)
        http_suffixes = ("backend_health", "listing_page", "search_v1", "login_me")
        for label in labels:
            for suffix in http_suffixes:
                require(f"{label}_{suffix}", "200")
        # Also reject failures in extra recorded app checks, not only the expected labels.
        for key in values:
            if key.endswith(tuple(f"_{suffix}" for suffix in http_suffixes)) and values[key] != "200":
                if not any(error.startswith(f"{key}:") for error in errors):
                    require(key, "200")
        require("verify_headers_local", "PASS")
        require("backend_only_restart_api", "PASS")
        seconds("backend_only_restart_serving_seconds")
        for label in ("daemon_graceful", "daemon_hard"):
            # The elapsed time includes restarting dockerd before wait_serving(600), so
            # 600 is not a ceiling on this total. Its timeout string is invalid evidence.
            serving(label, "first_page_seconds", "serving_seconds")
    else:
        errors.append(f"unknown drill kind: {kind}")
    return errors


def main(argv):
    if len(argv) != 2 or argv[0] not in ("recovery", "rollback"):
        print("usage: ops_drill_report.py {recovery|rollback} RESULTS", file=sys.stderr)
        return 2
    kind, path = argv
    data = load(path)
    # Keep the measured evidence available in summary.md even when acceptance fails.
    print(recovery(data) if kind == "recovery" else rollback(data))
    errors = validate(kind, data)
    print("\n## Outcome\n\n" + ("FAIL" if errors else "PASS"))
    for error in errors:
        print(f"- {error}")
        print(f"ops drill evidence: {error}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
