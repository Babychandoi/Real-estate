#!/usr/bin/env python3
"""Side-by-side table of every query-plans label (execution ms, warm/cold, flags) for the report."""
import json
import sys
from pathlib import Path


def label_key(label):
    size, _, phase = label.partition('-')
    return (int(size) if size.isdigit() else 0, phase != 'before')


def main():
    root = Path(sys.argv[1])
    labels = sorted((p.parent.name for p in root.glob('*/summary.json')), key=label_key)
    data = {label: json.loads((root / label / 'summary.json').read_text()) for label in labels}
    rows = {}
    for label, results in data.items():
        for r in results:
            rows.setdefault(r['name'], {})[(label, r['mode'])] = r
    columns = [(label, mode) for label in labels for mode in ('warm', 'cold')
               if any((label, mode) in v for v in rows.values())]
    print('\n## Execution time (ms) per dataset and cache state\n')
    print('| Query | ' + ' | '.join(f'{l} {m}' for l, m in columns) + ' | Indexes (largest) | Flags |')
    print('|---|' + '---:|' * len(columns) + '---|---|')
    for name, cells in rows.items():
        values = [f"{cells[c]['execution_ms']}" if c in cells else '-' for c in columns]
        last = cells.get(columns[-1]) or next(iter(cells.values()))
        flags = sorted({f for r in cells.values() for f in r['flags']})
        print(f"| {name} | " + ' | '.join(values) + f" | {', '.join(last['indexes']) or 'none'} | {', '.join(flags) or '-'} |")
    flagged = [(name, c, cells[c]['flags']) for name, cells in rows.items() for c in columns
               if c in cells and cells[c]['flags']]
    print(f'\n- Flagged plans (seq scan > 5000 rows on a core table, sort/temp spill): {len(flagged)}')
    for name, (label, mode), flags in flagged:
        print(f'  - {name} {label} {mode}: {", ".join(flags)}')


if __name__ == '__main__':
    main()
