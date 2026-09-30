#!/usr/bin/env python3
"""Read the pinned k6 summary; never include tokens or request bodies in evidence."""
import argparse
import json
import math
import re
from pathlib import Path


def metric(summary, name, statistic):
    entry = summary.get('metrics', {}).get(name, {})
    value = entry.get('values', entry).get(statistic)
    if (isinstance(value, bool) or not isinstance(value, (int, float))
            or not math.isfinite(value) or value < 0 or (statistic == 'rate' and value > 1)):
        raise ValueError(f'Missing or invalid metric: {name}/{statistic}')
    return value


def draft_count(summary):
    count = metric(summary, 'drafts_created', 'count')
    if count <= 0 or count != int(count):
        raise ValueError('Expected a positive integer successful-draft count')
    return int(count)


def verify_engine_state(summary, expected):
    reads = metric(summary, 'read_attempts', 'count')
    degraded = metric(summary, 'degraded_reads', 'count')
    if reads <= 0 or reads != int(reads) or degraded != int(degraded) or degraded != (reads if expected else 0):
        raise ValueError('Search engine state mismatch across measured read attempts')


def verify_redis_state(text, expected):
    samples = re.findall(r'^bds_ratelimit_redis_available(?:\{[^\n]*\})?\s+(\S+)', text, re.MULTILINE)
    if len(samples) != 1 or float(samples[0]) != expected:
        raise ValueError('Redis limiter state does not match the injected outage/recovery')


def report(summary):
    lines = ['## Measured endpoint results', '',
             '| Endpoint | p50 (ms) | p95 (ms) | p99 (ms) | HTTP error rate |',
             '|---|---:|---:|---:|---:|']
    for scenario in ['reads', 'writes']:
        timing = f'http_req_duration{{scenario:{scenario}}}'
        values = [metric(summary, timing, key) for key in ['med', 'p(95)', 'p(99)']]
        error = metric(summary, f'http_req_failed{{scenario:{scenario}}}', 'rate')
        lines.append(f'| {scenario} | {values[0]:.2f} | {values[1]:.2f} | {values[2]:.2f} | {error:.4%} |')
    lines += ['', f'- Successful draft creates: {draft_count(summary)}',
              f'- Dropped iterations: {metric(summary, "dropped_iterations", "count"):.0f}',
              '- See summary.json and k6.txt for throughput, checks and threshold outcomes.', '']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('summary', type=Path)
    parser.add_argument('--count', action='store_true')
    parser.add_argument('--expected-degraded', type=int, choices=[0, 1])
    parser.add_argument('--redis-state', type=int, choices=[0, 1])
    parser.add_argument('--metrics', type=Path)
    args = parser.parse_args()
    try:
        summary = json.loads(args.summary.read_text())
        if args.expected_degraded is not None:
            verify_engine_state(summary, args.expected_degraded)
        if args.redis_state is not None:
            if not args.metrics:
                raise ValueError('--redis-state requires --metrics')
            verify_redis_state(args.metrics.read_text(), args.redis_state)
        print(draft_count(summary) if args.count else report(summary))
    except (ValueError, OSError, TypeError, AttributeError) as error:
        parser.exit(2, f'Invalid k6 evidence: {error}\n')


if __name__ == '__main__':
    main()
