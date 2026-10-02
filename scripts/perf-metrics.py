#!/usr/bin/env python3
"""Server-side evidence of one load phase: deltas between the before/after Prometheus scrapes and database snapshots,
plus the job backlog sampled during the phase. Prints a Markdown section; never fails a phase on its own.

  perf-metrics.py PHASE_DIR
reads PHASE_DIR/{before,after}.prometheus, {before,after}.db.txt and backlog.csv (written by scripts/ci-mixed-load.sh).
"""
import csv
import re
import sys
from pathlib import Path

SAMPLE = re.compile(r'^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?\s+(\S+)')
LABEL = re.compile(r'(\w+)="([^"]*)"')


def parse(text):
    out = []
    for line in text.splitlines():
        if line.startswith('#'):
            continue
        match = SAMPLE.match(line)
        if not match:
            continue
        try:
            value = float(match.group(3))
        except ValueError:
            continue
        out.append((match.group(1), dict(LABEL.findall(match.group(2) or '')), value))
    return out


def total(samples, name, **want):
    return sum(v for n, labels, v in samples if n == name and all(
        (labels.get(k, '').startswith(w[:-1]) if w.endswith('*') else labels.get(k) == w) for k, w in want.items()))


def delta(before, after, name, **want):
    return total(after, name, **want) - total(before, name, **want)


def db_snapshot(path):
    values = {}
    if path.exists():
        for line in path.read_text().splitlines():
            key, _, value = line.partition('|')
            if value.strip():
                values[key.strip()] = float(value)
    return values


def main():
    phase = Path(sys.argv[1])
    before = parse((phase / 'before.prometheus').read_text()) if (phase / 'before.prometheus').exists() else []
    after = parse((phase / 'after.prometheus').read_text()) if (phase / 'after.prometheus').exists() else []
    lines = ['### Server-side metrics (deltas over the phase)', '']
    requests = delta(before, after, 'http_server_requests_seconds_count')
    errors5 = delta(before, after, 'http_server_requests_seconds_count', status='5*')
    limited = delta(before, after, 'http_server_requests_seconds_count', status='429')
    lines.append(f'- HTTP requests handled by the backend: {requests:.0f}; 5xx: {errors5:.0f}; 429: {limited:.0f}'
                 + (f' (server error rate {errors5 / requests:.4%})' if requests else ''))
    lag_count = delta(before, after, 'bds_search_index_lag_seconds_count')
    if lag_count:
        lag_sum = delta(before, after, 'bds_search_index_lag_seconds_sum')
        within = {le: delta(before, after, 'bds_search_index_lag_seconds_bucket', le=le) for le in ('1.0', '5.0', '10.0', '30.0')}
        quantiles = {q: total(after, 'bds_search_index_lag_seconds', quantile=q) for q in ('0.5', '0.95', '0.99')}
        lag_max = total(after, 'bds_search_index_lag_seconds_max')
        lines.append(f'- Search index lag (job created -> index write, bds.search.index.lag): {lag_count:.0f} writes, '
                     f'mean {lag_sum / lag_count:.3f} s; within 1 s {within["1.0"] / lag_count:.2%}, 5 s '
                     f'{within["5.0"] / lag_count:.2%}, 10 s {within["10.0"] / lag_count:.2%}, 30 s {within["30.0"] / lag_count:.2%}; '
                     f'end-of-phase window p50/p95/p99 {quantiles["0.5"]:.3f}/{quantiles["0.95"]:.3f}/{quantiles["0.99"]:.3f} s, '
                     f'max {lag_max:.3f} s')
    else:
        lines.append('- Search index lag: no index writes in this phase')
    engine = {f'{l.get("engine")}/{l.get("outcome")}': 0.0 for n, l, _ in after if n == 'bds_search_requests_total'}
    for key in engine:
        e, o = key.split('/')
        engine[key] = delta(before, after, 'bds_search_requests_total', engine=e, outcome=o)
    engine = {k: v for k, v in engine.items() if v}
    if engine:
        lines.append('- Search engine calls (engine/outcome): ' + ', '.join(f'{k} {v:.0f}' for k, v in sorted(engine.items())))
    cache = {}
    for n, labels, _ in after:
        if n == 'bds_search_cache_total':
            key = (labels.get('cache'), labels.get('result'))
            cache[key] = delta(before, after, 'bds_search_cache_total', cache=key[0], result=key[1])
    for name in sorted({c for c, _ in cache}):
        parts = {r: v for (c, r), v in cache.items() if c == name and v}
        if parts:
            lines.append(f'- Cache {name}: ' + ', '.join(f'{r} {v:.0f}' for r, v in sorted(parts.items())))
    timeouts = delta(before, after, 'hikaricp_connections_timeout_total')
    lines.append(f'- Hikari: connection timeouts {timeouts:.0f}; pending at end {total(after, "hikaricp_connections_pending"):.0f}; '
                 f'max pool {total(after, "hikaricp_connections_max"):.0f}')
    processed = {l.get('queue'): 0.0 for n, l, _ in after if n == 'bds_jobs_processed_total'}
    for queue in processed:
        processed[queue] = delta(before, after, 'bds_jobs_processed_total', queue=queue)
    processed = {q: v for q, v in processed.items() if v}
    if processed:
        lines.append('- Jobs processed: ' + ', '.join(f'{q} {v:.0f}' for q, v in sorted(processed.items())))
    lines.append(f'- Dead-lettered jobs (gauge at end): {total(after, "bds_jobs_dead"):.0f}')
    db_before, db_after = db_snapshot(phase / 'before.db.txt'), db_snapshot(phase / 'after.db.txt')
    if db_before and db_after:
        d = {k: db_after.get(k, 0) - db_before.get(k, 0) for k in db_after}
        statements = d.get('statements')
        lines.append(f'- PostgreSQL: {d.get("xact_commit", 0):.0f} commits, {d.get("xact_rollback", 0):.0f} rollbacks, '
                     f'blocks hit {d.get("blks_hit", 0):.0f} / read {d.get("blks_read", 0):.0f}, '
                     f'temp bytes {d.get("temp_bytes", 0):.0f}, deadlocks {d.get("deadlocks", 0):.0f}')
        if statements is not None and requests:
            lines.append(f'- SQL statements (pg_stat_statements, top level, incl. background jobs): {statements:.0f} '
                         f'= {statements / requests:.2f} per backend HTTP request')
    backlog = phase / 'backlog.csv'
    if backlog.exists():
        rows = list(csv.DictReader(backlog.open()))
        if rows:
            def peak(column):
                return max(float(r[column] or 0) for r in rows)
            lines.append(f'- Job backlog sampled every 5 s ({len(rows)} samples): max pending {peak("pending"):.0f} '
                         f'(search-index {peak("search_index_pending"):.0f}), oldest pending search-index job '
                         f'{peak("search_index_oldest_s"):.1f} s, oldest pending any queue {peak("oldest_s"):.1f} s, '
                         f'dead-lettered {peak("dead"):.0f}, unprocessed outbox events {peak("outbox"):.0f}; '
                         f'pending at end {float(rows[-1]["pending"] or 0):.0f}')
    print('\n'.join(lines) + '\n')


if __name__ == '__main__':
    main()
