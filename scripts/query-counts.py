#!/usr/bin/env python3
"""SQL statements per request type, measured with pg_stat_statements on the isolated perf stack (R-5 "query count").

For each request type: reset the statistics, wait an idle window and record which statements the background workers
run on their own (job polling, schedulers); reset again, send N sequential requests of that type, and count the calls of
every other top-level statement. statements/request = those calls / N. Statements also seen in the idle window are
reported separately (as background), never silently dropped. Writes query-counts.json and prints a Markdown table.

  query-counts.py --psql 'docker compose ... exec -T postgres psql -U bds -d DB' --base-url http://127.0.0.1:3000 \
      --env-file K6_ENV --listings 100000 --out DIR [--types a,b] [--n 30]
"""
import argparse
import json
import random
import shlex
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

STATS = """SELECT queryid, calls, left(regexp_replace(query, '\\s+', ' ', 'g'), 200)
FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
  AND query NOT LIKE '%pg_stat_statements%' AND calls > 0;"""


def psql(cmd, sql):
    result = subprocess.run(shlex.split(cmd) + ['-X', '-At', '-F', '\t', '-v', 'ON_ERROR_STOP=1'], input=sql,
                            text=True, capture_output=True, timeout=120)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip()[:500])
    return result.stdout


def snapshot(cmd):
    stats = {}
    for line in psql(cmd, STATS).splitlines():
        queryid, calls, text = line.split('\t', 2)
        stats[queryid] = (int(calls), text)
    return stats


def call(base, method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(base + path, data=data, method=method)
    if token:
        request.add_header('Authorization', 'Bearer ' + token)
    if data is not None:
        request.add_header('Content-Type', 'application/json')
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = response.read()
            return response.status, json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        return error.code, None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--psql', required=True)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--env-file', required=True, type=Path)
    parser.add_argument('--listings', required=True, type=int)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--types')
    parser.add_argument('--n', type=int, default=30)
    parser.add_argument('--idle', type=float, default=6.0)
    args = parser.parse_args()
    env = dict(line.split('=', 1) for line in args.env_file.read_text().splitlines() if '=' in line)
    broker = json.loads(env['PERF_ACTOR_TOKENS_JSON'])[0]
    moderator = env.get('PERF_MODERATOR_TOKEN')
    base = args.base_url
    rng = random.Random(7)
    counter = {'n': 0}

    def unique():
        counter['n'] += 1
        return counter['n']

    def publish():
        marker = 'qc' + ''.join(rng.choice('abcdefghijklmnopqrstuvwxyz') for _ in range(12))
        status, draft = call(base, 'POST', '/api/v1/listings', broker, {
            'title': f'Bán căn hộ {marker} đếm truy vấn', 'purpose': 'SALE', 'propertyType': 'APARTMENT',
            'priceVnd': 3100000000, 'areaM2': 70, 'description': 'Căn hộ kiểm thử đếm truy vấn, không có thật.',
            'provinceCode': '01', 'districtCode': '005', 'addressSummary': 'Cầu Giấy, Hà Nội',
            'publicLatitude': 21.03, 'publicLongitude': 105.79, 'imageUrls': []})
        if status != 201:
            return status
        status, _ = call(base, 'POST', f"/api/v1/listings/{draft['listingId']}/submit", broker)
        if status != 200:
            return status
        status, _ = call(base, 'POST', f"/api/v1/moderation/listings/{draft['listingId']}/approve", moderator,
                         {'revisionId': draft['revisionId'], 'reasonCode': 'MEETS_STANDARDS'})
        return status

    types = {
        # The same first page each time: served from the Redis first-page cache after the warm-up request.
        'search-first-page-cached': lambda: call(base, 'GET', '/api/v2/listings/search?purpose=SALE&size=24')[0],
        # A different price range each time: no cache hit, Elasticsearch (or the database while it is down).
        'search-filtered-uncached': lambda: call(
            base, 'GET', f'/api/v2/listings/search?purpose=SALE&district=005&priceMin={1000000000 + unique()}'
                         f'&priceMax=9000000000&size=24')[0],
        'search-keyword-uncached': lambda: call(
            base, 'GET', f'/api/v2/listings/search?q=can%20ho&priceMin={unique()}&size=24')[0],
        'detail-uncached': lambda: call(base, 'GET', f'/api/v2/listings/perf-{rng.randint(1, args.listings)}')[0],
        'detail-cached': lambda: call(base, 'GET', '/api/v2/listings/perf-1')[0],
        'map-clusters-zoom11': lambda: call(
            base, 'GET', f'/api/v2/listings/map?zoom=11&bbox=105.70,20.95,105.90,21.10&priceMin={unique()}')[0],
        'map-points-zoom15': lambda: call(
            base, 'GET', f'/api/v2/listings/map?zoom=15&bbox=105.7850,21.0280,105.7950,21.0340&priceMin={unique()}')[0],
        'seller-listings': lambda: call(base, 'GET', '/api/v2/public/sellers/'
                                        '00000000-0000-0000-0000-000000000000/listings')[0],
        'create-draft': lambda: call(base, 'POST', '/api/v1/listings', broker, {
            'title': f'Query count draft {unique()}', 'purpose': 'SALE', 'propertyType': 'APARTMENT',
            'priceVnd': 2500000000, 'areaM2': 60, 'description': 'Isolated performance fixture', 'imageUrls': []})[0],
        'publish-create-submit-approve': publish,
    }
    # The seller page needs a real seller id: the owner of perf-1.
    status, detail = call(base, 'GET', '/api/v2/listings/perf-1')
    if status == 200 and detail and detail.get('seller', {}).get('id'):
        seller = detail['seller']['id']
        types['seller-listings'] = lambda: call(base, 'GET', f'/api/v2/public/sellers/{seller}/listings?size=24')[0]
    wanted = args.types.split(',') if args.types else list(types)
    results = []
    for name in wanted:
        if name == 'publish-create-submit-approve' and not moderator:
            continue
        types[name]()  # warm-up (fills the caches the cached variants rely on)
        psql(args.psql, 'SELECT pg_stat_statements_reset();')
        time.sleep(args.idle)
        idle = snapshot(args.psql)
        psql(args.psql, 'SELECT pg_stat_statements_reset();')
        started = time.monotonic()
        statuses = [types[name]() for _ in range(args.n)]
        elapsed = time.monotonic() - started
        stats = snapshot(args.psql)
        request_calls = {q: v for q, v in stats.items() if q not in idle}
        background = {q: v for q, v in stats.items() if q in idle}
        per_request = sum(c for c, _ in request_calls.values()) / args.n
        results.append({
            'type': name, 'n': args.n, 'elapsed_s': round(elapsed, 2), 'statuses': sorted(set(statuses)),
            'ok': all(s in (200, 201) for s in statuses), 'statements_per_request': round(per_request, 2),
            'background_calls': sum(c for c, _ in background.values()),
            'statements': sorted(({'calls': c, 'per_request': round(c / args.n, 2), 'sql': t}
                                  for c, t in request_calls.values()), key=lambda s: -s['calls']),
            'background_statements': sorted(({'calls': c, 'sql': t} for c, t in background.values()),
                                            key=lambda s: -s['calls']),
        })
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / 'query-counts.json').write_text(json.dumps(results, indent=1, ensure_ascii=False))
    print('| Request type | requests | HTTP statuses | SQL statements / request | distinct statements | background calls (excluded) |')
    print('|---|---:|---|---:|---:|---:|')
    for r in results:
        print(f"| {r['type']} | {r['n']} | {','.join(map(str, r['statuses']))} | {r['statements_per_request']} | "
              f"{len(r['statements'])} | {r['background_calls']} |")
    print('\nPer-statement calls are in query-counts.json. A statement the workers also ran while idle is counted as '
          'background even if the request ran it too, so the figures are a lower bound.\n')
    if not all(r['ok'] for r in results):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
