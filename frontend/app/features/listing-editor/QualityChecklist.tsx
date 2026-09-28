import { CheckCircle2, CircleHelp, TriangleAlert } from 'lucide-react';
import type { QualityReport } from './api';

const ICONS = {
  PASS: { Icon: CheckCircle2, className: 'text-success', label: 'Đạt' },
  WARN: { Icon: TriangleAlert, className: 'text-warning', label: 'Nên bổ sung' },
  NO_DATA: { Icon: CircleHelp, className: 'text-on-surface-variant', label: 'Chưa đủ dữ liệu' },
} as const;

/** Server-computed quality checklist (guidance only, it never blocks submission). */
export function QualityChecklist({ report, headingLevel = 2 }: { report: QualityReport | null; headingLevel?: 2 | 3 }) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  return (
    <section aria-labelledby="quality-heading" className="rounded-xl border border-outline-variant bg-surface p-4">
      <Heading id="quality-heading" className="text-body font-semibold text-on-surface">
        Chất lượng tin {report ? `(${report.passed}/${report.total})` : ''}
      </Heading>
      <p className="mt-1 text-label text-on-surface-variant">
        Gợi ý để tin dễ được duyệt và được quan tâm; không bắt buộc.
      </p>
      {!report ? (
        <p className="mt-3 text-body-sm text-on-surface-variant">Lưu bản nháp để xem đánh giá.</p>
      ) : (
        <ul className="mt-3 flex flex-col gap-2" data-testid="quality-checklist">
          {report.items.map((item) => {
            const { Icon, className, label } = ICONS[item.status];
            return (
              <li key={item.code} className="flex gap-2 text-body-sm">
                <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${className}`} aria-hidden="true" />
                <div>
                  <span className="font-medium text-on-surface">{item.label}</span>
                  <span className="sr-only">: {label}</span>
                  {item.hint && <p className="text-label text-on-surface-variant">{item.hint}</p>}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
