import { useCallback, useEffect, useRef, useState } from 'react';
import { Check, History, Info } from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { billingApi, newIdempotencyKey } from '@/entities/admin/api/adminApi';
import type { BillingOrder, OrderEvent } from '@/entities/admin/model/types';
import { errorMessage } from '@/shared/api/errors';
import { ApiProblemException } from '@/shared/types/problem-details';
import { StatusBadge, formatDateTime, formatVnd } from '@/shared/admin/adminUi';
import { ORDER_STATUS } from '@/entities/admin/model/billingStatus';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Pagination } from '@/shared/ui/Pagination';
import { Dialog } from '@/shared/ui/Dialog';
import { Skeleton } from '@/shared/ui/Skeleton';

type Plan = { code: string; name: string; priceVnd: number; quota: number; durationDays: number; description: string };

const EVENT_LABELS: Record<string, string> = {
  CREATED: 'Tạo yêu cầu',
  TRANSFER_REPORTED: 'Bạn báo đã chuyển khoản',
  EXCEPTION: 'Khoản nhận chưa khớp',
  APPROVED: 'Đã kích hoạt gói',
  REJECTED: 'Không được duyệt',
  REFUNDED: 'Đã hoàn tiền',
  CANCELLED: 'Đã hủy',
};

export function BillingPage() {
  const [plans, setPlans] = useState<Plan[] | null>(null);
  const [orders, setOrders] = useState<BillingOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<{ order: BillingOrder; events: OrderEvent[] } | null>(null);
  // One key per plan intention: double clicks and retries return the same order.
  const keys = useRef<Record<string, string>>({});

  const load = useCallback(async (): Promise<BillingOrder[] | null> => {
    try {
      const [p, o] = await Promise.all([apiClient<Plan[]>('/billing/plans'), billingApi.myOrders(page)]);
      setPlans(p);
      setOrders(o.items);
      setTotal(o.total);
      return o.items;
    } catch (err) {
      setError(errorMessage(err, 'Không tải được gói dịch vụ.'));
      setPlans((p) => p ?? []);
      return null;
    }
  }, [page]);
  useEffect(() => {
    void load();
  }, [load]);

  const run = async (id: string, action: () => Promise<unknown>) => {
    if (busy) return;
    setBusy(id);
    setError(null);
    try {
      await action();
      await load();
    } catch (err) {
      if (err instanceof ApiProblemException && err.problem.status === 409) {
        // The order changed meanwhile (e.g. an admin recorded the payment while you cancelled): show the new state.
        const fresh = await load();
        const now = fresh?.find((o) => o.id === id);
        setError(
          now
            ? `Yêu cầu vừa được xử lý nên thao tác chưa được thực hiện. Trạng thái hiện tại: ${ORDER_STATUS[now.status].label}.`
            : 'Yêu cầu vừa được xử lý nên thao tác chưa được thực hiện. Danh sách đã được tải lại.',
        );
      } else {
        setError(errorMessage(err, 'Không thể thực hiện thao tác.'));
      }
    } finally {
      setBusy(null);
    }
  };
  const buy = (plan: Plan) =>
    run(plan.code, async () => {
      keys.current[plan.code] ??= newIdempotencyKey(`order-${plan.code}`);
      const order = await billingApi.createOrder(plan.code, keys.current[plan.code]);
      if (order.status !== 'CREATED') delete keys.current[plan.code];
    });
  const open = orders.filter(
    (o) => o.status === 'CREATED' || o.status === 'TRANSFER_REPORTED' || o.status === 'EXCEPTION',
  );

  return (
    <section className="mx-auto max-w-6xl space-y-8 px-4 py-10 md:px-8" data-ready={plans ? 'true' : undefined}>
      <header className="border-b border-slate-200 pb-8">
        <h1 className="text-3xl font-bold tracking-tight text-slate-950 md:text-4xl">Gói đăng tin</h1>
        <InlineFeedback kind="info" title="Đây là phí dịch vụ đăng tin trên Nhà Đất Chuẩn" className="mt-3">
          Khoản thanh toán này chỉ mua lượt đăng tin. Không phải tiền đặt cọc hay thanh toán bất động sản — không chuyển
          tiền cọc qua trang này.
        </InlineFeedback>
      </header>
      {error && <InlineFeedback kind="error" title={error} />}

      <section aria-labelledby="plans-heading">
        <h2 id="plans-heading" className="text-xl font-bold">
          Chọn gói
        </h2>
        {!plans ? (
          <Skeleton className="mt-3 h-40" />
        ) : (
          <ul className="mt-3 grid grid-cols-1 gap-4 md:grid-cols-3">
            {plans.map((plan) => {
              const existing = open.find((o) => o.planCode === plan.code);
              return (
                <li key={plan.code} className="flex flex-col rounded-xl border border-outline-variant p-5">
                  <h3 className="text-lg font-bold">{plan.name}</h3>
                  <p className="mt-1 text-2xl font-bold">
                    {plan.priceVnd === 0 ? 'Miễn phí' : formatVnd(plan.priceVnd)}
                  </p>
                  <ul className="mt-3 flex-1 space-y-1 text-sm">
                    <li className="flex gap-2">
                      <Check className="h-4 w-4 shrink-0" aria-hidden="true" /> {plan.quota} lượt đăng tin
                    </li>
                    <li className="flex gap-2">
                      <Check className="h-4 w-4 shrink-0" aria-hidden="true" /> Hiệu lực {plan.durationDays} ngày
                    </li>
                    <li className="text-on-surface-variant">{plan.description}</li>
                  </ul>
                  {plan.priceVnd > 0 &&
                    (existing ? (
                      <p className="mt-4 text-sm font-semibold">
                        Bạn đã có yêu cầu đang mở cho gói này ({ORDER_STATUS[existing.status].label}).
                      </p>
                    ) : (
                      <Button className="mt-4" isLoading={busy === plan.code} onClick={() => buy(plan)}>
                        Tạo yêu cầu thanh toán
                      </Button>
                    ))}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {open
        .filter((o) => o.status === 'CREATED')
        .map((o) => (
          <section
            key={o.id}
            aria-label={`Hướng dẫn chuyển khoản ${o.reference}`}
            className="rounded-xl border border-primary/40 p-5"
          >
            <h2 className="text-lg font-bold">Chuyển khoản cho gói {o.planName}</h2>
            <div className="mt-3 grid gap-4 md:grid-cols-[200px_1fr]">
              {o.qrUrl && (
                <img src={o.qrUrl} alt={`Mã QR chuyển khoản ${o.reference}`} className="w-48 rounded-md border" />
              )}
              <dl className="grid grid-cols-2 gap-2 text-sm">
                <dt className="text-on-surface-variant">Số tiền</dt>
                <dd className="font-bold">{formatVnd(o.amountVnd)}</dd>
                <dt className="text-on-surface-variant">Nội dung chuyển khoản</dt>
                <dd className="font-mono font-bold">{o.reference}</dd>
                <dt className="text-on-surface-variant">Số tài khoản</dt>
                <dd>{o.accountNumberSnapshot}</dd>
                <dt className="text-on-surface-variant">Chủ tài khoản</dt>
                <dd>{o.accountNameSnapshot}</dd>
              </dl>
            </div>
            <p className="mt-3 text-xs text-on-surface-variant">
              Thông tin tài khoản được chốt tại thời điểm tạo yêu cầu.
            </p>
            <div className="mt-4 flex flex-wrap justify-between gap-6">
              <Button isLoading={busy === o.id} onClick={() => run(o.id, () => billingApi.report(o.id))}>
                Tôi đã chuyển khoản
              </Button>
              <Button
                variant="outline"
                disabled={busy !== null}
                onClick={() => run(o.id, () => billingApi.cancel(o.id))}
              >
                Hủy yêu cầu
              </Button>
            </div>
          </section>
        ))}

      <section aria-labelledby="history-heading">
        <h2 id="history-heading" className="text-xl font-bold">
          Lịch sử thanh toán
        </h2>
        {orders.length === 0 ? (
          <EmptyState title="Chưa có yêu cầu thanh toán nào" headingLevel={3} />
        ) : (
          <ul className="mt-3 divide-y divide-outline-variant rounded-xl border border-outline-variant">
            {orders.map((o) => (
              <li key={o.id} className="flex flex-wrap items-center justify-between gap-3 p-4 text-sm">
                <div>
                  <p className="font-semibold">
                    {o.planName} · {formatVnd(o.amountVnd)} · <span className="font-mono">{o.reference}</span>
                  </p>
                  <p className="text-on-surface-variant">
                    {formatDateTime(o.createdAt)} · {ORDER_STATUS[o.status].hint}
                  </p>
                  {o.reviewNote && o.status !== 'APPROVED' && <p>Ghi chú: {o.reviewNote}</p>}
                </div>
                <div className="flex items-center gap-2">
                  <StatusBadge label={ORDER_STATUS[o.status].label} variant={ORDER_STATUS[o.status].variant} />
                  <Button
                    size="sm"
                    variant="ghost"
                    leftIcon={<History className="h-4 w-4" />}
                    aria-label={`Lịch sử ${o.reference}`}
                    onClick={async () => setDetail(await billingApi.myOrder(o.id))}
                  >
                    Lịch sử
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
        {total > 10 && (
          <Pagination
            className="mt-3"
            page={page + 1}
            pageCount={Math.ceil(total / 10)}
            onPageChange={(p) => setPage(p - 1)}
          />
        )}
      </section>

      {detail && (
        <Dialog
          size="lg"
          open
          onClose={() => setDetail(null)}
          title={`Yêu cầu ${detail.order.reference}`}
          description={ORDER_STATUS[detail.order.status].label}
          footer={
            <Button variant="outline" onClick={() => setDetail(null)}>
              Đóng
            </Button>
          }
        >
          <ol className="space-y-2 text-sm">
            {detail.events.map((e) => (
              <li key={e.id} className="rounded-md bg-surface-container-low p-2">
                <span className="font-semibold">{EVENT_LABELS[e.type] ?? e.type}</span> · {formatDateTime(e.createdAt)}
                {e.note && <span className="block text-on-surface-variant">{e.note}</span>}
              </li>
            ))}
          </ol>
          {detail.order.invoiceNumber && <p className="mt-3 text-sm">Hóa đơn: {detail.order.invoiceNumber}</p>}
          <p className="mt-3 flex gap-2 text-xs text-on-surface-variant">
            <Info className="h-4 w-4 shrink-0" aria-hidden="true" />
            Gói, số tiền và tài khoản nhận là bản chốt tại lúc tạo yêu cầu.
          </p>
        </Dialog>
      )}
    </section>
  );
}

export default BillingPage;
