import { useCallback, useEffect, useState } from 'react';
import { History, RefreshCw } from 'lucide-react';
import { billingApi } from '@/entities/admin/api/adminApi';
import type {
  AdminOrder,
  AdminOrderPage,
  BankSettings,
  BillingOrder,
  OrderEvent,
  OrderStatus,
} from '@/entities/admin/model/types';
import { ApiProblemException } from '@/shared/types/problem-details';
import { errorMessage } from '@/shared/api/errors';
import { ReasonDialog, StatusBadge, formatDateTime, formatVnd } from '@/shared/admin/adminUi';
import { ORDER_STATUS } from '@/entities/admin/model/billingStatus';
import { Button } from '@/shared/ui/Button';
import { Chip } from '@/shared/ui/Chip';
import { DataTable, type DataTableColumn, type DataTableStatus } from '@/shared/ui/DataTable';
import { Dialog } from '@/shared/ui/Dialog';
import { EmptyState } from '@/shared/ui/EmptyState';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Pagination } from '@/shared/ui/Pagination';
import { Sheet } from '@/shared/ui/Sheet';
import { TextInput } from '@/shared/ui/TextInput';

const QUEUE: OrderStatus[] = [
  'TRANSFER_REPORTED',
  'EXCEPTION',
  'CREATED',
  'APPROVED',
  'REJECTED',
  'REFUNDED',
  'CANCELLED',
];
const EXCEPTION_LABELS: Record<string, string> = {
  AMOUNT_MISMATCH: 'Sai số tiền',
  REFERENCE_MISMATCH: 'Sai nội dung chuyển khoản',
  AMOUNT_AND_REFERENCE_MISMATCH: 'Sai số tiền và nội dung',
};
const RESOLUTIONS = [
  { code: 'APPROVE_WITH_NOTE', label: 'Duyệt kèm ghi chú (cộng lượt)' },
  { code: 'REJECT', label: 'Từ chối' },
  { code: 'REFUNDED_OFFLINE', label: 'Đã hoàn tiền ngoài hệ thống' },
];

export function AdminBillingPage() {
  const [status, setStatus] = useState<OrderStatus>('TRANSFER_REPORTED');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<AdminOrderPage | null>(null);
  const [tableStatus, setTableStatus] = useState<DataTableStatus>('loading');
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; title: string } | null>(null);
  const [receipt, setReceipt] = useState<AdminOrder | null>(null);
  const [resolve, setResolve] = useState<AdminOrder | null>(null);
  const [detail, setDetail] = useState<{ order: BillingOrder; events: OrderEvent[] } | null>(null);

  const load = useCallback(async () => {
    setTableStatus((s) => (s === 'loading' ? 'loading' : 'refreshing'));
    try {
      setData(await billingApi.reconciliation(status, page));
      setTableStatus('ready');
    } catch {
      setTableStatus('error');
    }
  }, [status, page]);
  useEffect(() => {
    void load();
  }, [load]);

  const columns: DataTableColumn<AdminOrder>[] = [
    {
      key: 'order',
      header: 'Đơn',
      cell: (o) => (
        <div>
          <p className="font-mono font-semibold">{o.reference}</p>
          <p className="text-xs text-on-surface-variant">
            {o.customerName} · {o.planName}
          </p>
        </div>
      ),
    },
    { key: 'amount', header: 'Số tiền', cell: (o) => formatVnd(o.amountVnd) },
    {
      key: 'received',
      header: 'Đã nhận',
      cell: (o) =>
        o.receivedAmountVnd == null ? (
          'Chưa ghi nhận'
        ) : (
          <span>
            {formatVnd(o.receivedAmountVnd)}
            {o.exceptionReason && (
              <span className="block text-xs font-semibold">
                {EXCEPTION_LABELS[o.exceptionReason] ?? o.exceptionReason}
              </span>
            )}
          </span>
        ),
    },
    { key: 'reported', header: 'Báo chuyển lúc', cell: (o) => formatDateTime(o.reportedAt) },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (o) => <StatusBadge label={ORDER_STATUS[o.status].label} variant={ORDER_STATUS[o.status].variant} />,
    },
    {
      key: 'actions',
      header: 'Thao tác',
      align: 'end',
      cell: (o) => (
        <div className="flex justify-end gap-2">
          <Button
            size="sm"
            variant="ghost"
            leftIcon={<History className="h-4 w-4" />}
            aria-label={`Lịch sử ${o.reference}`}
            onClick={async () => setDetail(await billingApi.adminOrder(o.id))}
          >
            Lịch sử
          </Button>
          {(o.status === 'TRANSFER_REPORTED' || o.status === 'CREATED') && (
            <Button size="sm" onClick={() => setReceipt(o)} aria-label={`Ghi nhận tiền về ${o.reference}`}>
              Ghi nhận tiền về
            </Button>
          )}
          {o.status === 'EXCEPTION' && (
            <Button size="sm" onClick={() => setResolve(o)} aria-label={`Xử lý ngoại lệ ${o.reference}`}>
              Xử lý ngoại lệ
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6" data-ready={tableStatus === 'loading' ? undefined : 'true'}>
      <header>
        <h1 className="text-2xl font-bold">Đơn hàng và đối soát</h1>
        <p className="mt-1 text-sm text-on-surface-variant">
          Ghi nhận đúng số tiền và nội dung đã về tài khoản. Khớp chính xác thì gói được kích hoạt một lần; lệch thì vào
          hàng ngoại lệ để quyết định có ghi chú.
        </p>
      </header>
      <div className="flex flex-wrap gap-2" role="group" aria-label="Lọc theo trạng thái">
        {QUEUE.map((s) => (
          <Chip
            key={s}
            size="sm"
            selected={status === s}
            onClick={() => {
              setStatus(s);
              setPage(0);
            }}
          >
            {ORDER_STATUS[s].label}
            {data?.counts[s] != null ? ` (${data.counts[s]})` : ''}
          </Chip>
        ))}
        <Button size="sm" variant="ghost" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => void load()}>
          Tải lại
        </Button>
      </div>
      {feedback && <InlineFeedback kind={feedback.kind} title={feedback.title} />}
      <DataTable
        caption="Đơn cần đối soát, báo chuyển sớm nhất ở đầu"
        columns={columns}
        rows={data?.items ?? []}
        getRowId={(o) => o.id}
        status={tableStatus}
        onRetry={() => void load()}
        empty={<EmptyState title="Không có đơn nào ở trạng thái này" />}
        footer={
          data && data.total > data.size ? (
            <Pagination
              page={page + 1}
              pageCount={Math.ceil(data.total / data.size)}
              onPageChange={(p) => setPage(p - 1)}
            />
          ) : undefined
        }
      />
      <BankSettingsPanel />
      {receipt && (
        <ReceiptDialog
          order={receipt}
          onClose={() => setReceipt(null)}
          onDone={async (o) => {
            setFeedback({ kind: 'success', title: `${o.reference}: ${ORDER_STATUS[o.status].label}` });
            await load();
          }}
        />
      )}
      <ReasonDialog
        open={resolve !== null}
        title={resolve ? `Xử lý ngoại lệ ${resolve.reference}` : ''}
        description={
          resolve
            ? `Cần ${formatVnd(resolve.amountVnd)}, đã nhận ${formatVnd(resolve.receivedAmountVnd)} (${EXCEPTION_LABELS[resolve.exceptionReason ?? ''] ?? ''}).`
            : undefined
        }
        choiceLabel="Cách xử lý"
        reasons={RESOLUTIONS}
        noteLabel="Ghi chú xử lý"
        noteMinLength={5}
        confirmLabel="Lưu quyết định"
        onClose={() => setResolve(null)}
        onConfirm={async (code, note) => {
          if (!resolve) return;
          const o = await billingApi.resolve(resolve.id, code as 'APPROVE_WITH_NOTE', note);
          setFeedback({ kind: 'success', title: `${o.reference}: ${ORDER_STATUS[o.status].label}` });
          await load();
        }}
      />
      {detail && (
        <Sheet
          open
          onClose={() => setDetail(null)}
          title={`Lịch sử ${detail.order.reference}`}
          description={ORDER_STATUS[detail.order.status].label}
        >
          <ol className="space-y-2 text-sm">
            {detail.events.map((e) => (
              <li key={e.id} className="rounded-md bg-surface-container-low p-2">
                <span className="font-semibold">{e.type}</span>{' '}
                {e.fromStatus ? `(${e.fromStatus} sang ${e.toStatus})` : ''} · {e.actorName ?? 'Hệ thống'} ·{' '}
                {formatDateTime(e.createdAt)}
                {e.note && <span className="block text-on-surface-variant">{e.note}</span>}
              </li>
            ))}
          </ol>
        </Sheet>
      )}
    </div>
  );
}

function ReceiptDialog({
  order,
  onClose,
  onDone,
}: {
  order: AdminOrder;
  onClose: () => void;
  onDone: (o: AdminOrder) => Promise<void>;
}) {
  const [amount, setAmount] = useState(String(order.amountVnd));
  const [reference, setReference] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async () => {
    const value = Number(amount.replace(/\D/g, ''));
    if (!amount.trim() || Number.isNaN(value)) return setError('Nhập số tiền đã nhận.');
    setBusy(true);
    try {
      const o = await billingApi.receipt(order.id, value, reference, note || undefined);
      await onDone(o);
      onClose();
    } catch (err) {
      setError(errorMessage(err, 'Không lưu được.'));
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open
      onClose={onClose}
      title={`Ghi nhận tiền về: ${order.reference}`}
      description={`Cần nhận ${formatVnd(order.amountVnd)} với nội dung chứa ${order.reference}.`}
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={onClose} disabled={busy}>
            Hủy
          </Button>
          <Button onClick={submit} isLoading={busy}>
            Lưu đối soát
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        <FormField label="Số tiền đã nhận (đ)" required>
          {(c) => <TextInput {...c} inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value)} />}
        </FormField>
        <FormField label="Nội dung chuyển khoản trên sao kê" required>
          {(c) => <TextInput {...c} value={reference} onChange={(e) => setReference(e.target.value)} />}
        </FormField>
        <FormField label="Ghi chú">
          {(c) => <TextInput {...c} value={note} onChange={(e) => setNote(e.target.value)} />}
        </FormField>
        {error && <InlineFeedback kind="error" title={error} />}
      </div>
    </Dialog>
  );
}

function BankSettingsPanel() {
  const [bank, setBank] = useState<BankSettings | null>(null);
  const [form, setForm] = useState({ bankBin: '', bankName: '', accountNumber: '', accountName: '', adminEmail: '' });
  const [msg, setMsg] = useState<{ kind: 'success' | 'error' | 'conflict'; title: string } | null>(null);
  const load = useCallback(async () => {
    const b = await billingApi.bank();
    setBank(b ?? null);
    if (b)
      setForm({
        bankBin: b.bankBin,
        bankName: b.bankName,
        accountNumber: b.accountNumber,
        accountName: b.accountName,
        adminEmail: b.adminEmail ?? '',
      });
  }, []);
  useEffect(() => {
    void load().catch(() => undefined);
  }, [load]);
  const save = async () => {
    try {
      setBank(await billingApi.saveBank({ ...form, adminEmail: form.adminEmail || null }, bank?.version ?? null));
      setMsg({ kind: 'success', title: 'Đã lưu tài khoản nhận tiền.' });
    } catch (err) {
      const conflict = err instanceof ApiProblemException && err.problem.status === 409;
      setMsg({ kind: conflict ? 'conflict' : 'error', title: errorMessage(err, 'Không lưu được.') });
    }
  };
  const field = (key: keyof typeof form, label: string) => (
    <FormField label={label}>
      {(c) => <TextInput {...c} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} />}
    </FormField>
  );
  return (
    <section aria-labelledby="bank-heading" className="rounded-lg border border-outline-variant p-4">
      <h2 id="bank-heading" className="text-lg font-bold">
        Tài khoản nhận tiền
      </h2>
      <p className="text-sm text-on-surface-variant">
        Phiên bản {bank?.version ?? 'chưa cấu hình'}. Nếu người khác vừa sửa, bạn sẽ được yêu cầu tải lại trước khi lưu.
      </p>
      <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
        {field('bankBin', 'Mã BIN ngân hàng')}
        {field('bankName', 'Tên ngân hàng')}
        {field('accountNumber', 'Số tài khoản')}
        {field('accountName', 'Tên chủ tài khoản')}
        {field('adminEmail', 'Email nhận thông báo đối soát')}
      </div>
      {msg && (
        <InlineFeedback
          kind={msg.kind}
          title={msg.title}
          className="mt-3"
          action={
            msg.kind === 'conflict'
              ? {
                  label: 'Tải lại',
                  onClick: () => {
                    setMsg(null);
                    void load();
                  },
                }
              : undefined
          }
        />
      )}
      <Button className="mt-3" onClick={save}>
        Lưu
      </Button>
    </section>
  );
}

export default AdminBillingPage;
