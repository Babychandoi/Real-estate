#!/usr/bin/env python3
"""Read the pinned k6 summary; never include tokens or request bodies in evidence."""
import argparse
import json
import math
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
    args = parser.parse_args()
    try:
        summary = json.loads(args.summary.read_text())
        print(draft_count(summary) if args.count else report(summary))
    except (ValueError, OSError, TypeError, AttributeError) as error:
        parser.exit(2, f'Invalid k6 evidence: {error}\n')


if __name__ == '__main__':
    main()
