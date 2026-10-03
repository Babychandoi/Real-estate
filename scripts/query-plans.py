#!/usr/bin/env python3
"""EXPLAIN (ANALYZE, BUFFERS) of the application's query shapes (infra/perf/query-catalog.sql), warm and cold.

The column lists and the owner-page SELECT are read from the Java adapters at run time, so the plans always describe the
SQL the application sends. Usage (see scripts/ci-query-plans.sh):

  query-plans.py --psql 'docker exec -i PG psql -X -U bds -d bds_perf_x' --label 1m-before --out DIR \
      [--cold-reset 'COMMAND'] [--modes warm,cold]

warm: each statement runs twice, then EXPLAIN ANALYZE runs on the third execution (data and catalog cached).
cold: --cold-reset runs before every statement (restart PostgreSQL and drop the OS page cache), so shared buffers, the
      page cache and the catalog caches are empty; planning time then includes catalog loading.
"""
import argparse
import json
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADAPTER = ROOT / 'backend/src/main/java/com/company/bds/search/infrastructure/JdbcListingReadModelAdapter.java'
OWNER_QUERY = ROOT / 'backend/src/main/java/com/company/bds/listing/infrastructure/persistence/query/JdbcOwnerListingQuery.java'
CATALOG = ROOT / 'infra/perf/query-catalog.sql'


def java_string_constant(source, name):
    """Read a literal or concatenated literals; reject expressions instead of emitting invalid/misleading SQL."""
    match = re.search(r'\b' + re.escape(name) + r'\s*=\s*(.*?);', source, re.S)
    if not match:
        raise ValueError(f'missing Java constant {name}')
    expression = match.group(1)
    literal = r'"(?:[^"\\]|\\.)*"'
    if not re.fullmatch(r'\s*' + literal + r'(?:\s*\+\s*' + literal + r')*\s*', expression):
        raise ValueError(f'{name}: expected string literals only')
    return ''.join(json.loads(part) for part in re.findall(literal, expression))


def java_constants():
    adapter = ADAPTER.read_text()
    summary = re.search(r'SUMMARY_COLUMNS = """\s*\n(.*?)""";', adapter, re.S).group(1)
    summary = ' '.join(summary.split())
    owner_active = java_string_constant(adapter, 'OWNER_ACTIVE')
    detail = summary.replace('NULL::text AS description', 'description').replace('NULL::text[] AS media_urls', 'media_urls')
    owner = OWNER_QUERY.read_text()
    page = re.search(r'jdbc\.query\("""\s*\n(\s*SELECT l\.id, l\.slug.*?)WHERE l\.owner_id = \?', owner, re.S).group(1)
    return {'SUMMARY': summary, 'DETAIL': detail, 'OWNER_ACTIVE': owner_active, 'OWNER_PAGE': page.rstrip()}


def parse_catalog(text):
    params_sql = re.search(r'-- params\n(.*?;)\n', text, re.S).group(1)
    blocks = []
    for match in re.finditer(r'-- name: (\S+)\n-- why: ([^\n]*)\n(.*?;)\n', text, re.S):
        blocks.append({'name': match.group(1), 'why': match.group(2), 'sql': match.group(3).strip()})
    names = [b['name'] for b in blocks]
    if len(names) != len(set(names)) or not blocks:
        raise SystemExit('catalog: duplicate or missing query names')
    return params_sql, blocks


def psql(cmd, sql, timeout=1800):
    result = subprocess.run(shlex.split(cmd) + ['-At', '-v', 'ON_ERROR_STOP=1'], input=sql, text=True,
                            capture_output=True, timeout=timeout)
    if result.returncode != 0:
        raise RuntimeError(f'psql failed ({result.returncode}): {result.stderr.strip()[:2000]}')
    return result.stdout


def substitute(sql, values):
    def repl(match):
        key = match.group(1)
        if key not in values:
            raise KeyError(f'unknown placeholder @{key}@')
        return values[key]
    return re.sub(r'@([A-Za-z_]+)@', repl, sql)


def walk(node, depth, lines, flags):
    kind = node.get('Node Type')
    detail = [kind]
    for key in ('Index Name', 'Relation Name', 'Alias'):
        if node.get(key) and not (key == 'Alias' and node.get(key) == node.get('Relation Name')):
            detail.append(f'{key.split()[0].lower()}={node[key]}')
    if node.get('Sort Method'):
        detail.append(f"sort={node['Sort Method']}/{node.get('Sort Space Type')}/{node.get('Sort Space Used')}kB")
        if node.get('Sort Space Type') == 'Disk':
            flags.add('SORT_SPILL')
    loops = node.get('Actual Loops', 1)
    detail.append(f"rows={node.get('Actual Rows')}x{loops} time={node.get('Actual Total Time')}ms "
                  f"hit={node.get('Shared Hit Blocks', 0)} read={node.get('Shared Read Blocks', 0)}"
                  + (f" temp={node.get('Temp Read Blocks', 0)}/{node.get('Temp Written Blocks', 0)}"
                     if node.get('Temp Read Blocks') or node.get('Temp Written Blocks') else ''))
    if node.get('Rows Removed by Filter'):
        detail.append(f"removed_by_filter={node['Rows Removed by Filter']}")
    lines.append('  ' * depth + ' '.join(str(d) for d in detail))
    if kind == 'Seq Scan' and node.get('Relation Name') in {'listing_public_read', 'listings', 'listing_revisions', 'leads'}:
        rows_seen = (node.get('Actual Rows', 0) + node.get('Rows Removed by Filter', 0)) * loops
        if rows_seen > 5000:
            flags.add(f"SEQ_SCAN:{node['Relation Name']}")
    if node.get('Temp Written Blocks'):
        flags.add('TEMP_SPILL')
    for child in node.get('Plans', []):
        walk(child, depth + 1, lines, flags)


def indexes_used(node, out):
    if node.get('Index Name'):
        out.add(node['Index Name'])
    for child in node.get('Plans', []):
        indexes_used(child, out)
    return out


def explain(cmd, sql, warm):
    prefix = 'SET track_io_timing = on;\nSET statement_timeout = \'10min\';\n'
    if warm:
        psql(cmd, prefix + sql + '\n' + sql + '\n')
    raw = psql(cmd, prefix + 'EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON) ' + sql + '\n')
    start = raw.index('[')
    return json.loads(raw[start:])[0]


def summarise(name, why, mode, plan):
    root = plan['Plan']
    lines, flags = [], set()
    walk(root, 0, lines, flags)
    io_read = root.get('I/O Read Time') or plan.get('Planning', {}).get('I/O Read Time') or 0
    return {
        'name': name, 'why': why, 'mode': mode,
        'planning_ms': round(plan.get('Planning Time', 0), 3),
        'execution_ms': round(plan.get('Execution Time', 0), 3),
        'rows': root.get('Actual Rows'),
        'shared_hit': root.get('Shared Hit Blocks', 0), 'shared_read': root.get('Shared Read Blocks', 0),
        'temp_written': root.get('Temp Written Blocks', 0), 'io_read_ms': round(io_read, 3),
        'indexes': sorted(indexes_used(root, set())), 'flags': sorted(flags), 'tree': lines,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--psql', required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--modes', default='warm,cold')
    parser.add_argument('--cold-reset')
    parser.add_argument('--only', help='comma-separated query names')
    args = parser.parse_args()
    modes = args.modes.split(',')
    if 'cold' in modes and not args.cold_reset:
        raise SystemExit('cold mode requires --cold-reset')
    constants = java_constants()
    params_sql, blocks = parse_catalog(CATALOG.read_text())
    if args.only:
        wanted = set(args.only.split(','))
        blocks = [b for b in blocks if b['name'] in wanted]
    values = dict(constants)
    for line in psql(args.psql, params_sql).splitlines():
        key, _, value = line.partition('|')
        if not value:
            raise SystemExit(f'params: no value for {key}')
        values[key] = value
    out = args.out / args.label
    out.mkdir(parents=True, exist_ok=True)
    (out / 'params.json').write_text(json.dumps({k: v for k, v in values.items() if k not in constants}, indent=2))
    results = []
    for mode in modes:
        for block in blocks:
            sql = substitute(block['sql'], values)
            if mode == 'cold':
                subprocess.run(args.cold_reset, shell=True, check=True, timeout=300)
            started = time.monotonic()
            plan = explain(args.psql, sql, warm=(mode == 'warm'))
            summary = summarise(block['name'], block['why'], mode, plan)
            summary['wall_s'] = round(time.monotonic() - started, 2)
            results.append(summary)
            (out / f"{block['name']}.{mode}.json").write_text(json.dumps({'sql': sql, 'plan': plan}, indent=1))
            print(f"{args.label} {mode:4} {block['name']:45} plan={summary['planning_ms']:>9}ms "
                  f"exec={summary['execution_ms']:>10}ms read={summary['shared_read']:>7} {' '.join(summary['flags'])}",
                  flush=True)
    (out / 'summary.json').write_text(json.dumps(results, indent=1))
    lines = [f'# Query plans: {args.label}', '',
             '| Query | Mode | Planning ms | Execution ms | Rows | Shared hit | Shared read | I/O read ms | Indexes | Flags |',
             '|---|---|---:|---:|---:|---:|---:|---:|---|---|']
    for r in results:
        lines.append(f"| {r['name']} | {r['mode']} | {r['planning_ms']} | {r['execution_ms']} | {r['rows']} | "
                     f"{r['shared_hit']} | {r['shared_read']} | {r['io_read_ms']} | {', '.join(r['indexes']) or '-'} | "
                     f"{', '.join(r['flags']) or '-'} |")
    lines += ['', '## Plan trees', '']
    for r in results:
        lines += [f"### {r['name']} ({r['mode']})", '', r['why'], '', '```', *r['tree'], '```', '']
    (out / 'summary.md').write_text('\n'.join(lines))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, KeyError, subprocess.SubprocessError) as error:
        print(f'query-plans: {error}', file=sys.stderr)
        sys.exit(1)
