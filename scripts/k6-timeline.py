#!/usr/bin/env python3
"""Per-interval latency of a k6 run (from `k6 run --out csv=FILE.gz`): shows when a phase was slow, e.g. how long the
Elasticsearch breaker-opening transition lasted or whether a soak degraded over time. Prints a Markdown table.

  k6-timeline.py SAMPLES.csv.gz [--bucket 10]
"""
import argparse
import csv
import gzip
import math
from collections import defaultdict


def percentile(values, q):
    if not values:
        return float('nan')
    values = sorted(values)
    return values[min(len(values) - 1, max(0, math.ceil(q * len(values)) - 1))]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('samples')
    parser.add_argument('--bucket', type=int, default=10)
    args = parser.parse_args()
    durations = defaultdict(lambda: defaultdict(list))
    degraded = defaultdict(float)
    dropped = defaultdict(float)
    failed = defaultdict(float)
    start = None
    with gzip.open(args.samples, 'rt', newline='') as handle:
        for row in csv.DictReader(handle):
            name = row.get('metric_name')
            if name not in ('http_req_duration', 'degraded_reads', 'dropped_iterations', 'http_req_failed'):
                continue
            t = float(row['timestamp'])
            start = t if start is None else min(start, t)
            bucket = int(t)
            value = float(row['metric_value'])
            scenario = row.get('scenario') or '-'
            if name == 'http_req_duration':
                durations[bucket][scenario].append(value)
            elif name == 'degraded_reads':
                degraded[bucket] += value
            elif name == 'dropped_iterations':
                dropped[bucket] += value
            elif value:
                failed[bucket] += value
    if start is None:
        print('- No k6 samples recorded.\n')
        return
    start = int(start)
    grouped = defaultdict(lambda: {'reads': [], 'writes': [], 'publish': [], 'degraded': 0.0, 'dropped': 0.0, 'failed': 0.0})
    for bucket, scenarios in durations.items():
        key = (bucket - start) // args.bucket
        for scenario, values in scenarios.items():
            grouped[key].setdefault(scenario, []).extend(values)
    for source, field in ((degraded, 'degraded'), (dropped, 'dropped'), (failed, 'failed')):
        for bucket, value in source.items():
            grouped[(bucket - start) // args.bucket][field] += value
    print(f'| t (s) | reads | reads p50 | reads p95 | reads p99 | reads max | writes p95 | writes max | degraded searches | dropped | failed |')
    print('|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|')
    for key in sorted(grouped):
        g = grouped[key]
        reads, writes = g['reads'], g['writes']
        print(f"| {key * args.bucket} | {len(reads)} | {percentile(reads, .5):.0f} | {percentile(reads, .95):.0f} | "
              f"{percentile(reads, .99):.0f} | {max(reads) if reads else float('nan'):.0f} | {percentile(writes, .95):.0f} | "
              f"{max(writes) if writes else float('nan'):.0f} | {g['degraded']:.0f} | {g['dropped']:.0f} | {g['failed']:.0f} |")
    print()


if __name__ == '__main__':
    main()
