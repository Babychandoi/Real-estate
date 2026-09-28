import { useCallback, useEffect, useState } from 'react';
import { Copy, Link2, Link2Off, ListPlus, LogOut, Trash2, Users } from 'lucide-react';
import { ListingCard } from '@/entities/listing/ui/ListingCard';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { RadioGroup } from '@/shared/ui/Radio';
import { Select } from '@/shared/ui/Select';
import { Skeleton } from '@/shared/ui/Skeleton';
import { Switch } from '@/shared/ui/Switch';
import { TextInput } from '@/shared/ui/TextInput';
import { useToast } from '@/shared/ui/Toast';
import { cn } from '@/shared/ui/cn';
import {
  engagementApi,
  problemCode,
  problemDetail,
  type ShareRole,
  type ShortlistDetail,
  type ShortlistRole,
  type ShortlistSummary,
} from '../api';

const ROLE_LABELS: Record<ShortlistRole, string> = { OWNER: 'Chủ danh sách', EDITOR: 'Được sửa', VIEWER: 'Chỉ xem' };

interface ShortlistsPanelProps {
  shortlists: ShortlistSummary[];
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  onChanged: () => void;
}

/** Shared shortlists (P-02): roles, share link with a role, members, mute, leave. */
export function ShortlistsPanel({ shortlists, selectedId, onSelect, onChanged }: ShortlistsPanelProps) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    setCreating(true);
    setCreateError(null);
    try {
      const created = await engagementApi.createShortlist(name);
      setName('');
      onChanged();
      onSelect(created.id);
      toast.show({ kind: 'success', title: 'Đã tạo danh sách' });
    } catch (error) {
      setCreateError(problemDetail(error, 'Chưa tạo được danh sách.'));
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[18rem_1fr]">
      <div className="flex flex-col gap-4">
        <form onSubmit={create} className="flex flex-col gap-2 rounded-card border border-outline-variant p-4">
          <FormField label="Danh sách mới" error={createError ?? undefined}>
            {(control) => (
              <TextInput
                {...control}
                value={name}
                maxLength={80}
                required
                placeholder="Ví dụ: Nhà cho bố mẹ"
                onChange={(event) => setName(event.target.value)}
              />
            )}
          </FormField>
          <Button type="submit" size="sm" isLoading={creating} leftIcon={<ListPlus className="h-4 w-4" />}>
            Tạo danh sách
          </Button>
        </form>
        {shortlists.length === 0 ? (
          <p className="text-body-sm text-on-surface-variant">
            Gom các tin đang cân nhắc vào một danh sách và chia sẻ cho người thân cùng xem hoặc cùng sửa.
          </p>
        ) : (
          <ul className="flex flex-col gap-1" aria-label="Các danh sách của bạn">
            {shortlists.map((list) => (
              <li key={list.id}>
                <button
                  type="button"
                  aria-current={list.id === selectedId ? 'true' : undefined}
                  onClick={() => onSelect(list.id)}
                  className={cn(
                    'flex min-h-11 w-full flex-col items-start rounded-lg px-3 py-2 text-left hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
                    list.id === selectedId && 'bg-primary/10',
                  )}
                >
                  <span className="text-sm font-semibold text-on-surface">{list.name}</span>
                  <span className="text-xs text-on-surface-variant">
                    {list.itemCount} tin · {ROLE_LABELS[list.role]}
                    {list.shared ? ' · Đang chia sẻ' : ''}
                    {list.muted ? ' · Đã tắt thông báo' : ''}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="min-w-0">
        {selectedId ? (
          <ShortlistDetailView
            key={selectedId}
            id={selectedId}
            onChanged={onChanged}
            onGone={() => {
              onSelect(null);
              onChanged();
            }}
          />
        ) : (
          <EmptyState
            icon={Users}
            title="Chọn một danh sách"
            description="Hoặc tạo danh sách mới rồi thêm tin từ mục Tin đã lưu."
          />
        )}
      </div>
    </div>
  );
}

function ShortlistDetailView({ id, onChanged, onGone }: { id: string; onChanged: () => void; onGone: () => void }) {
  const toast = useToast();
  const [detail, setDetail] = useState<ShortlistDetail | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error' | 'gone'>('loading');
  const [shareOpen, setShareOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [renameError, setRenameError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setStatus('loading');
    try {
      setDetail(await engagementApi.shortlist(id));
      setStatus('ready');
    } catch (error) {
      setStatus(problemCode(error) === 'SHORTLIST_NOT_FOUND' ? 'gone' : 'error');
    }
  }, [id]);
  useEffect(() => {
    void load();
  }, [load]);

  const run = async (action: () => Promise<ShortlistDetail | void>, success?: string) => {
    try {
      const next = await action();
      if (next) setDetail(next);
      onChanged();
      if (success) toast.show({ kind: 'success', title: success });
    } catch (error) {
      toast.show({
        kind: 'error',
        title: 'Chưa thực hiện được',
        description: problemDetail(error, 'Vui lòng thử lại.'),
      });
    }
  };

  if (status === 'loading') return <Skeleton className="h-64 rounded-card" />;
  if (status === 'gone') {
    return (
      <EmptyState title="Danh sách không còn" description="Danh sách đã bị xóa hoặc bạn không còn là thành viên." />
    );
  }
  if (status === 'error' || !detail) return <ErrorState title="Không tải được danh sách" onRetry={() => void load()} />;
  const owner = detail.role === 'OWNER';
  const canEdit = detail.role !== 'VIEWER';

  const rename = async (event: React.FormEvent) => {
    event.preventDefault();
    if (renaming === null) return;
    setRenameError(null);
    try {
      setDetail(await engagementApi.renameShortlist(id, renaming, detail.version));
      setRenaming(null);
      onChanged();
    } catch (error) {
      if (problemCode(error) === 'SHORTLIST_VERSION_CONFLICT') {
        setRenameError('Danh sách vừa được thay đổi ở nơi khác. Đã tải lại, hãy thử lại.');
        void load();
      } else setRenameError(problemDetail(error, 'Chưa đổi được tên.'));
    }
  };

  return (
    <section aria-labelledby="shortlist-heading" className="flex flex-col gap-4">
      <header className="flex flex-wrap items-start justify-between gap-3">
        {renaming !== null ? (
          <form onSubmit={rename} className="flex flex-wrap items-end gap-2">
            <FormField label="Tên danh sách" error={renameError ?? undefined}>
              {(control) => (
                <TextInput
                  {...control}
                  value={renaming}
                  maxLength={80}
                  required
                  onChange={(e) => setRenaming(e.target.value)}
                />
              )}
            </FormField>
            <Button type="submit" size="sm">
              Lưu tên
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => setRenaming(null)}>
              Hủy
            </Button>
          </form>
        ) : (
          <div>
            <h2 id="shortlist-heading" className="text-headline-sm text-on-surface">
              {detail.name}
            </h2>
            <p className="text-body-sm text-on-surface-variant">
              {detail.itemCount}/{detail.itemLimit} tin · {ROLE_LABELS[detail.role]}
            </p>
          </div>
        )}
        <div className="flex flex-wrap gap-2">
          {owner && renaming === null && (
            <Button variant="ghost" size="sm" onClick={() => setRenaming(detail.name)}>
              Đổi tên
            </Button>
          )}
          {owner && (
            <Button
              variant="outline"
              size="sm"
              leftIcon={<Link2 className="h-4 w-4" />}
              onClick={() => setShareOpen(true)}
            >
              {detail.share?.shared ? 'Tạo lại liên kết' : 'Chia sẻ'}
            </Button>
          )}
          {owner && detail.share?.shared && (
            <Button
              variant="ghost"
              size="sm"
              leftIcon={<Link2Off className="h-4 w-4" />}
              onClick={() =>
                void run(async () => {
                  await engagementApi.revokeShare(id);
                  return engagementApi.shortlist(id);
                }, 'Đã thu hồi liên kết; thành viên hiện có vẫn giữ quyền.')
              }
            >
              Thu hồi liên kết
            </Button>
          )}
          {!owner && (
            <Button
              variant="ghost"
              size="sm"
              leftIcon={<LogOut className="h-4 w-4" />}
              onClick={() =>
                void (async () => {
                  try {
                    await engagementApi.leave(id);
                    onGone();
                  } catch {
                    toast.show({ kind: 'error', title: 'Chưa rời được danh sách' });
                  }
                })()
              }
            >
              Rời danh sách
            </Button>
          )}
          {owner && (
            <Button
              variant="danger"
              size="sm"
              leftIcon={<Trash2 className="h-4 w-4" />}
              onClick={() => setConfirmDelete(true)}
            >
              Xóa danh sách
            </Button>
          )}
        </div>
      </header>

      {owner && detail.share && (
        <p className="text-body-sm text-on-surface-variant">
          {detail.share.shared
            ? `Đang chia sẻ bằng liên kết với quyền “${ROLE_LABELS[detail.share.role ?? 'VIEWER']}”. Người có liên kết thấy tên danh sách, tên gọi của bạn và các tin đang hiển thị.`
            : 'Chưa chia sẻ. Chỉ bạn thấy danh sách này.'}
        </p>
      )}
      {!owner && (
        <Switch
          checked={!detail.muted}
          onCheckedChange={(on) => void run(() => engagementApi.mute(id, !on))}
          label="Nhận thông báo khi danh sách thay đổi"
          description="Tắt để ngừng nhận thông báo của riêng danh sách này."
        />
      )}

      {detail.items.length === 0 ? (
        <EmptyState
          title="Danh sách chưa có tin"
          description={canEdit ? 'Thêm tin từ mục Tin đã lưu.' : 'Chủ danh sách chưa thêm tin nào.'}
          headingLevel={3}
        />
      ) : (
        <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {detail.items.map((item) => (
            <li key={item.listingId} className="flex min-w-0 flex-col gap-2">
              {item.listing ? (
                <ListingCard listing={item.listing} />
              ) : (
                <div className="rounded-card border border-dashed border-outline-variant p-4">
                  <p className="text-body font-semibold text-on-surface">{item.unavailable?.title ?? 'Tin đăng'}</p>
                  <p className="text-body-sm text-on-surface-variant">Tin không còn hiển thị.</p>
                </div>
              )}
              {canEdit && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() =>
                    void run(() => engagementApi.removeFromShortlist(id, item.listingId), 'Đã bỏ khỏi danh sách')
                  }
                >
                  Bỏ khỏi danh sách
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {detail.members.length > 0 && (
        <section aria-labelledby="members-heading" className="rounded-card border border-outline-variant p-4">
          <h3 id="members-heading" className="text-body font-semibold text-on-surface">
            Thành viên ({detail.members.length})
          </h3>
          <ul className="mt-2 divide-y divide-outline-variant/50">
            {detail.members.map((member, index) => (
              <li key={member.userId ?? `m-${index}`} className="flex flex-wrap items-center gap-3 py-2">
                <span className="min-w-0 flex-1 text-sm text-on-surface">{member.name}</span>
                {owner && member.userId ? (
                  <>
                    <Select
                      aria-label={`Quyền của ${member.name}`}
                      value={member.role}
                      onChange={(event) =>
                        void run(() =>
                          engagementApi.setMemberRole(id, member.userId as string, event.target.value as ShareRole),
                        )
                      }
                      options={[
                        { value: 'VIEWER', label: ROLE_LABELS.VIEWER },
                        { value: 'EDITOR', label: ROLE_LABELS.EDITOR },
                      ]}
                    />
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() =>
                        void run(() => engagementApi.removeMember(id, member.userId as string), 'Đã xóa thành viên')
                      }
                    >
                      Xóa
                    </Button>
                  </>
                ) : (
                  <Badge>{ROLE_LABELS[member.role]}</Badge>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {shareOpen && (
        <ShareDialog
          id={id}
          onClose={() => setShareOpen(false)}
          onShared={() => {
            onChanged();
            void load();
          }}
        />
      )}
      <Dialog
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        title="Xóa danh sách này?"
        description="Danh sách, liên kết chia sẻ và quyền của thành viên sẽ bị xóa. Tin đã lưu của bạn không bị ảnh hưởng."
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Giữ lại
            </Button>
            <Button
              variant="danger"
              onClick={() =>
                void (async () => {
                  try {
                    await engagementApi.deleteShortlist(id);
                    setConfirmDelete(false);
                    onGone();
                  } catch {
                    toast.show({ kind: 'error', title: 'Chưa xóa được danh sách' });
                  }
                })()
              }
            >
              Xóa danh sách
            </Button>
          </>
        }
      />
    </section>
  );
}

function ShareDialog({ id, onClose, onShared }: { id: string; onClose: () => void; onShared: () => void }) {
  const [role, setRole] = useState<ShareRole>('VIEWER');
  const [link, setLink] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await engagementApi.share(id, role);
      setLink(`${window.location.origin}${result.path}`);
      onShared();
    } catch (failure) {
      setError(problemDetail(failure, 'Chưa tạo được liên kết.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open
      onClose={onClose}
      title="Chia sẻ danh sách"
      description="Tạo liên kết mới sẽ vô hiệu hóa liên kết cũ. Người mở liên kết và đăng nhập sẽ tham gia với quyền bạn chọn."
      footer={
        link ? (
          <Button onClick={onClose}>Xong</Button>
        ) : (
          <>
            <Button variant="ghost" onClick={onClose}>
              Hủy
            </Button>
            <Button onClick={create} isLoading={busy}>
              Tạo liên kết
            </Button>
          </>
        )
      }
    >
      {error && <InlineFeedback kind="error" title={error} />}
      {link ? (
        <div className="flex flex-col gap-2">
          <FormField
            label="Liên kết chia sẻ"
            hint="Liên kết chỉ hiển thị một lần; hãy sao chép và gửi cho người bạn muốn."
          >
            {(control) => <TextInput {...control} value={link} readOnly onFocus={(e) => e.target.select()} />}
          </FormField>
          <Button
            variant="outline"
            leftIcon={<Copy className="h-4 w-4" />}
            onClick={() =>
              void navigator.clipboard?.writeText(link).then(
                () => setCopied(true),
                () => setCopied(false),
              )
            }
          >
            {copied ? 'Đã sao chép' : 'Sao chép liên kết'}
          </Button>
        </div>
      ) : (
        <RadioGroup
          legend="Người có liên kết được"
          name="share-role"
          value={role}
          onChange={setRole}
          options={[
            { value: 'VIEWER', label: 'Chỉ xem', description: 'Xem các tin trong danh sách.' },
            { value: 'EDITOR', label: 'Xem và sửa', description: 'Thêm hoặc bỏ tin khỏi danh sách.' },
          ]}
        />
      )}
    </Dialog>
  );
}
