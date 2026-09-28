import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchLeadReport } from '@/entities/lead/api/leadApi';
import { formatMinutes, orNoData, problemMessage } from '@/entities/lead/model/labels';
import type { LeadReport } from '@/entities/lead/model/types';
import { ErrorState } from '@/shared/ui/ErrorState';
import { LoadingStatus } from '@/shared/ui/Skeleton';
import { Select } from '@/shared/ui/Select';

const PERIODS = [
  { value: '7', label: '7 ngày qua' },
  { value: '30', label: '30 ngày qua' },
  { value: '90', label: '90 ngày qua' },
];
const vnd = new Intl.NumberFormat('vi-VN');
const money = (value: number) => `${vnd.format(value)} đ`;
const percent = (value: number) => `${value.toLocaleString('vi-VN')}%`;

function isoDaysAgo(days: number) {
  const date = new Date(Date.now() - days * 86400000 + 7 * 3600000);
  return date.toISOString().slice(0, 10);
}

/**
 * Qualified-lead / ROI report (P-08). Every figure comes from recorded facts; anything that cannot be measured shows
 * "Chưa có dữ liệu" instead of 0 (no spend recorded → no cost per lead).
 */
export function LeadReportView() {
  const [days, setDays] = useState('30');
  const [report, setReport] = useState<LeadReport | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      setReport(await fetchLeadReport(isoDaysAgo(Number(days) - 1), isoDaysAgo(0)));
    } catch (caught) {
      setError(problemMessage(caught, 'Không tải được báo cáo hiệu quả lead.'));
    }
  }, [days]);
  useEffect(() => {
    void load();
  }, [load]);

  if (error) return <ErrorState title="Không tải được báo cáo" description={error} onRetry={() => void load()} />;
  if (!report) return <LoadingStatus label="Đang tải báo cáo…" />;

  const cards: Array<[string, string, string?]> = [
    ['Lead nhận được', String(report.leads), `${report.viewingRequests} yêu cầu hẹn xem`],
    [
      'Phản hồi đầu tiên (trung vị)',
      orNoData(report.medianFirstResponseMinutes, formatMinutes),
      `${report.measuredResponses} lead đã đo · mục tiêu ${report.targetMinutes} phút`,
    ],
    ['Phản hồi đúng hạn', orNoData(report.withinTargetPercent, percent)],
    [
      'Lead đủ điều kiện',
      String(report.qualified),
      `${report.unqualified} không đạt · ${report.unassessed} chưa đánh giá`,
    ],
    [
      'Có lịch hẹn được xác nhận',
      String(report.leadsWithConfirmedAppointment),
      orNoData(report.appointmentRatePercent, percent),
    ],
    ['Buổi xem đã diễn ra', String(report.appointmentsCompleted), `${report.appointmentsNoShow} vắng mặt`],
    ['Chi phí gói đã duyệt', orNoData(report.spendVnd, money), `${report.approvedOrders} đơn trong kỳ`],
    [
      'Chi phí / lead đủ điều kiện',
      orNoData(report.costPerQualifiedLeadVnd, money),
      orNoData(report.costPerLeadVnd, (v) => `${money(v)} / lead`),
    ],
  ];

  return (
    <section aria-labelledby="lead-report-title" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="lead-report-title" className="text-headline-sm text-on-surface">
            Hiệu quả lead
          </h2>
          <p className="text-body-sm text-on-surface-variant">
            Lead tạo từ {report.from} đến {report.to}. Chi phí chỉ tính các gói đăng tin đã được duyệt trong kỳ.
          </p>
        </div>
        <Select
          aria-label="Khoảng thời gian"
          options={PERIODS}
          value={days}
          onChange={(event) => setDays(event.target.value)}
        />
      </div>
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map(([label, value, hint]) => (
          <div key={label} className="rounded-lg border border-outline-variant p-4">
            <dt className="text-label font-normal text-on-surface-variant">{label}</dt>
            <dd className="mt-1 text-headline-sm text-on-surface">{value}</dd>
            {hint && <dd className="text-label font-normal text-on-surface-variant">{hint}</dd>}
          </div>
        ))}
      </dl>
      {report.byListing.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[28rem] text-left text-body-sm">
            <caption className="sr-only">Lead theo tin đăng</caption>
            <thead className="text-label text-on-surface-variant">
              <tr>
                <th className="py-2">Tin đăng</th>
                <th className="py-2 text-right">Lead</th>
                <th className="py-2 text-right">Đủ điều kiện</th>
                <th className="py-2 text-right">Có lịch hẹn</th>
              </tr>
            </thead>
            <tbody>
              {report.byListing.map((row) => (
                <tr key={row.listingId} className="border-t border-outline-variant">
                  <td className="py-2">
                    <Link
                      className="font-semibold text-primary hover:underline"
                      to={`/my-leads?listingId=${row.listingId}`}
                    >
                      {row.title}
                    </Link>
                  </td>
                  <td className="py-2 text-right">{row.leads}</td>
                  <td className="py-2 text-right">{row.qualified}</td>
                  <td className="py-2 text-right">{row.withAppointment}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
