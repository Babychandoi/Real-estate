import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, Eye, FilePlus2, History, Pencil, Send, Undo2 } from 'lucide-react';
import {
  cmsAdminApi,
  type AdminArticle,
  type AdminRevision,
  type ArticleInput,
  type RevisionInput,
} from '@/entities/content/adminApi';
import {
  ARTICLE_CATEGORIES,
  articleCategoryLabel,
  REVISION_STATUS_LABELS,
  type ArticleCategory,
} from '@/entities/content/model';
import { errorMessage } from '@/shared/api/errors';
import { ReasonDialog, StatusBadge, formatDateTime } from '@/shared/admin/adminUi';
import type { BadgeVariant } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { DataTable, type DataTableColumn, type DataTableStatus } from '@/shared/ui/DataTable';
import { Dialog } from '@/shared/ui/Dialog';
import { EmptyState } from '@/shared/ui/EmptyState';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Pagination } from '@/shared/ui/Pagination';
import { Select } from '@/shared/ui/Select';
import { Sheet } from '@/shared/ui/Sheet';
import { TextArea, TextInput } from '@/shared/ui/TextInput';

const PAGE_SIZE = 20;

const ARTICLE_STATUS: Record<AdminArticle['status'], { label: string; variant: BadgeVariant }> = {
  DRAFT: { label: 'Bản nháp', variant: 'neutral' },
  SUBMITTED: { label: 'Chờ duyệt', variant: 'warning' },
  PUBLISHED: { label: 'Đang công khai', variant: 'success' },
  ARCHIVED: { label: 'Đã gỡ', variant: 'neutral' },
  REJECTED: { label: 'Bị từ chối', variant: 'error' },
};
const REVISION_VARIANT: Record<AdminRevision['status'], BadgeVariant> = {
  DRAFT: 'neutral',
  SUBMITTED: 'warning',
  SCHEDULED: 'info',
  PUBLISHED: 'success',
  SUPERSEDED: 'neutral',
  REJECTED: 'error',
  ARCHIVED: 'neutral',
};

const EMPTY_REVISION: RevisionInput = {
  title: '',
  summary: '',
  contentHtml: '',
  coverImageUrl: '',
  authorName: '',
  legalReference: '',
  metaDescription: '',
  sourceName: '',
  sourceUrl: '',
};

function fromRevision(revision: AdminRevision | null | undefined): RevisionInput {
  if (!revision) return EMPTY_REVISION;
  return {
    title: revision.title,
    summary: revision.summary ?? '',
    contentHtml: revision.contentHtml,
    coverImageUrl: revision.coverImageUrl ?? '',
    authorName: revision.authorName,
    legalReference: revision.legalReference ?? '',
    metaDescription: revision.metaDescription ?? '',
    sourceName: revision.sourceName ?? '',
    sourceUrl: revision.sourceUrl ?? '',
  };
}

/** "2026-10-01T09:00" in the browser's zone → ISO instant. */
function localToIso(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

type EditorMode =
  | { kind: 'create' }
  | { kind: 'revision'; article: AdminArticle }
  | { kind: 'draft'; article: AdminArticle; revision: AdminRevision };

/**
 * Staff CMS (P-07, UI-25): articles with their workflow — draft → submitted → published now or at a set time, or
 * rejected with a reason; edits of public content are new revisions (published ones never change); preview links for
 * any revision; unpublishing takes the public page down (410). The public page is `/tin-tuc/<slug>`.
 */
export function CmsManagementPage() {
  const [status, setStatus] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<AdminArticle[]>([]);
  const [total, setTotal] = useState(0);
  const [tableStatus, setTableStatus] = useState<DataTableStatus>('loading');
  const [reload, setReload] = useState(0);
  const [openId, setOpenId] = useState<string | null>(null);
  const [editor, setEditor] = useState<EditorMode | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    const abort = new AbortController();
    setTableStatus('loading');
    cmsAdminApi
      .list({ status, category, page: page - 1, size: PAGE_SIZE }, abort.signal)
      .then((result) => {
        setRows(result.items);
        setTotal(result.total);
        setTableStatus('ready');
      })
      .catch(() => {
        if (!abort.signal.aborted) setTableStatus('error');
      });
    return () => abort.abort();
  }, [status, category, page, reload]);

  const refresh = useCallback(() => setReload((value) => value + 1), []);

  const columns: DataTableColumn<AdminArticle>[] = [
    {
      key: 'title',
      header: 'Bài viết',
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-medium text-on-surface">{row.currentRevision?.title ?? row.slug}</p>
          <p className="text-xs text-on-surface-variant">/tin-tuc/{row.slug}</p>
        </div>
      ),
    },
    { key: 'category', header: 'Chuyên mục', cell: (row) => articleCategoryLabel(row.category) },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (row) => (
        <div className="flex flex-col gap-1">
          <StatusBadge label={ARTICLE_STATUS[row.status].label} variant={ARTICLE_STATUS[row.status].variant} />
          {row.scheduledPublishAt && (
            <span className="text-xs text-on-surface-variant">Hẹn giờ {formatDateTime(row.scheduledPublishAt)}</span>
          )}
        </div>
      ),
    },
    {
      key: 'latest',
      header: 'Bản mới nhất',
      cell: (row) =>
        row.currentRevision
          ? `#${row.currentRevision.revisionNumber} · ${REVISION_STATUS_LABELS[row.currentRevision.status]}`
          : '—',
    },
    { key: 'updated', header: 'Cập nhật', cell: (row) => formatDateTime(row.updatedAt) },
    {
      key: 'open',
      header: <span className="sr-only">Thao tác</span>,
      align: 'end',
      cell: (row) => (
        <Button size="sm" variant="outline" onClick={() => setOpenId(row.id)}>
          Mở
        </Button>
      ),
    },
  ];

  return (
    <div className="ndc-admin-page flex flex-col gap-5" data-ready={tableStatus === 'loading' ? 'false' : 'true'}>
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1>Quản trị nội dung</h1>
          <p className="mt-1 text-sm text-on-surface-variant">
            Bài viết lên trang công khai chỉ sau khi được duyệt; mỗi lần sửa là một phiên bản mới, có thể hẹn giờ xuất
            bản.
          </p>
        </div>
        <Button leftIcon={<FilePlus2 className="h-4 w-4" />} onClick={() => setEditor({ kind: 'create' })}>
          Bài viết mới
        </Button>
      </header>
      {notice && (
        <InlineFeedback kind="success" title={notice}>
          {null}
        </InlineFeedback>
      )}
      <div className="flex flex-wrap gap-3">
        <FormField label="Trạng thái" className="min-w-48">
          {(control) => (
            <Select
              {...control}
              value={status}
              placeholder="Tất cả trạng thái"
              options={Object.entries(ARTICLE_STATUS).map(([value, item]) => ({ value, label: item.label }))}
              onChange={(event) => {
                setStatus(event.target.value);
                setPage(1);
              }}
            />
          )}
        </FormField>
        <FormField label="Chuyên mục" className="min-w-48">
          {(control) => (
            <Select
              {...control}
              value={category}
              placeholder="Tất cả chuyên mục"
              options={ARTICLE_CATEGORIES}
              onChange={(event) => {
                setCategory(event.target.value);
                setPage(1);
              }}
            />
          )}
        </FormField>
      </div>
      <DataTable
        caption="Danh sách bài viết"
        columns={columns}
        rows={rows}
        getRowId={(row) => row.id}
        status={tableStatus}
        onRetry={refresh}
        empty={<EmptyState title="Chưa có bài viết" description="Tạo bài viết mới để bắt đầu." />}
      />
      {total > PAGE_SIZE && (
        <Pagination
          page={page}
          pageCount={Math.ceil(total / PAGE_SIZE)}
          onPageChange={setPage}
          label="Trang bài viết"
        />
      )}
      <ArticleSheet
        articleId={openId}
        onClose={() => setOpenId(null)}
        onChanged={(message) => {
          setNotice(message);
          refresh();
        }}
        onEdit={setEditor}
      />
      <ArticleEditor
        mode={editor}
        onClose={() => setEditor(null)}
        onSaved={(article, message) => {
          setEditor(null);
          setNotice(message);
          setOpenId(article.id);
          refresh();
        }}
      />
    </div>
  );
}

function ArticleSheet({
  articleId,
  onClose,
  onChanged,
  onEdit,
}: {
  articleId: string | null;
  onClose: () => void;
  onChanged: (message: string) => void;
  onEdit: (mode: EditorMode) => void;
}) {
  const [article, setArticle] = useState<AdminArticle | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rejecting, setRejecting] = useState<AdminRevision | null>(null);
  const [scheduleAt, setScheduleAt] = useState('');
  const [previewPath, setPreviewPath] = useState<string | null>(null);
  const [confirmUnpublish, setConfirmUnpublish] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (!articleId) {
      setArticle(null);
      return undefined;
    }
    const abort = new AbortController();
    setError(null);
    setPreviewPath(null);
    cmsAdminApi.detail(articleId, abort.signal).then(setArticle, (failure: unknown) => {
      if (!abort.signal.aborted) setError(errorMessage(failure, 'Không tải được bài viết.'));
    });
    return () => abort.abort();
  }, [articleId, version]);

  const run = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      setVersion((value) => value + 1);
      onChanged(message);
    } catch (failure) {
      setError(errorMessage(failure, 'Thao tác không thành công.'));
    } finally {
      setBusy(false);
    }
  };

  const latest = article?.revisions?.[0] ?? null;
  const live = article?.revisions?.find((revision) => revision.id === article.publishedRevisionId) ?? null;
  return (
    <Sheet
      open={Boolean(articleId)}
      onClose={onClose}
      title={latest?.title ?? 'Bài viết'}
      description={article ? `/tin-tuc/${article.slug} · ${articleCategoryLabel(article.category)}` : undefined}
    >
      {error && (
        <InlineFeedback kind="error" title="Có lỗi">
          {error}
        </InlineFeedback>
      )}
      {!article ? (
        !error && <p role="status">Đang tải…</p>
      ) : (
        <div className="flex flex-col gap-5 text-sm">
          <section className="flex flex-col gap-2">
            <StatusBadge
              label={ARTICLE_STATUS[article.status].label}
              variant={ARTICLE_STATUS[article.status].variant}
            />
            {article.publishedAt && <p>Công khai từ {formatDateTime(article.publishedAt)}</p>}
            {article.status === 'PUBLISHED' && (
              <a
                className="font-medium text-primary underline"
                href={article.publicPath}
                target="_blank"
                rel="noopener"
              >
                Xem trang công khai
              </a>
            )}
            {article.scheduledPublishAt && (
              <div className="flex flex-wrap items-center gap-2 rounded-lg bg-info-container p-3 text-info-on-container">
                <CalendarClock className="h-4 w-4" aria-hidden="true" />
                Hẹn xuất bản lúc {formatDateTime(article.scheduledPublishAt)}
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={busy}
                  onClick={() => run(() => cmsAdminApi.cancelSchedule(article.id), 'Đã hủy lịch xuất bản.')}
                >
                  Hủy lịch
                </Button>
              </div>
            )}
          </section>

          {latest && (
            <section
              className="flex flex-col gap-3 rounded-xl border border-outline-variant/40 p-4"
              aria-label="Bản mới nhất"
            >
              <p className="font-semibold">
                Bản #{latest.revisionNumber} · {REVISION_STATUS_LABELS[latest.status]}
              </p>
              {latest.rejectionReason && <p className="text-error">Lý do từ chối: {latest.rejectionReason}</p>}
              <div className="flex flex-wrap gap-2">
                {latest.status === 'DRAFT' && (
                  <>
                    <Button
                      size="sm"
                      variant="outline"
                      leftIcon={<Pencil className="h-4 w-4" />}
                      onClick={() => onEdit({ kind: 'draft', article, revision: latest })}
                    >
                      Sửa bản nháp
                    </Button>
                    <Button
                      size="sm"
                      leftIcon={<Send className="h-4 w-4" />}
                      disabled={busy}
                      onClick={() => run(() => cmsAdminApi.submit(article.id, latest.id), 'Đã nộp duyệt.')}
                    >
                      Nộp duyệt
                    </Button>
                  </>
                )}
                {latest.status === 'SUBMITTED' && (
                  <Button
                    size="sm"
                    disabled={busy}
                    onClick={() => run(() => cmsAdminApi.approve(article.id, latest.id, null), 'Đã duyệt và xuất bản.')}
                  >
                    Duyệt và xuất bản ngay
                  </Button>
                )}
                {(latest.status === 'SUBMITTED' || latest.status === 'SCHEDULED') && (
                  <Button size="sm" variant="danger" disabled={busy} onClick={() => setRejecting(latest)}>
                    Từ chối
                  </Button>
                )}
                <Button
                  size="sm"
                  variant="ghost"
                  leftIcon={<Eye className="h-4 w-4" />}
                  disabled={busy}
                  onClick={async () => {
                    try {
                      const link = await cmsAdminApi.previewLink(article.id, latest.id);
                      setPreviewPath(link.path);
                    } catch (failure) {
                      setError(errorMessage(failure, 'Không tạo được liên kết xem trước.'));
                    }
                  }}
                >
                  Liên kết xem trước
                </Button>
              </div>
              {latest.status === 'SUBMITTED' && (
                <div className="flex flex-wrap items-end gap-2">
                  <FormField
                    label="Hẹn giờ xuất bản"
                    hint="Giờ theo máy của bạn; trang công khai hiện bài từ thời điểm này."
                  >
                    {(control) => (
                      <TextInput
                        {...control}
                        type="datetime-local"
                        value={scheduleAt}
                        onChange={(event) => setScheduleAt(event.target.value)}
                      />
                    )}
                  </FormField>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={busy || !localToIso(scheduleAt)}
                    onClick={() =>
                      run(
                        () => cmsAdminApi.approve(article.id, latest.id, localToIso(scheduleAt)),
                        'Đã duyệt và hẹn giờ xuất bản.',
                      )
                    }
                  >
                    Duyệt và hẹn giờ
                  </Button>
                </div>
              )}
              {previewPath && (
                <p className="break-all rounded-lg bg-surface-container-low p-3">
                  Liên kết xem trước (24 giờ, không lập chỉ mục):{' '}
                  <a className="text-primary underline" href={previewPath} target="_blank" rel="noopener">
                    {window.location.origin}
                    {previewPath}
                  </a>
                </p>
              )}
            </section>
          )}

          {latest && latest.status !== 'DRAFT' && (
            <Button
              variant="outline"
              leftIcon={<Pencil className="h-4 w-4" />}
              onClick={() => onEdit({ kind: 'revision', article })}
            >
              Tạo bản sửa mới {live ? 'từ bản đang công khai' : ''}
            </Button>
          )}
          {article.status === 'PUBLISHED' && (
            <Button
              variant="danger"
              leftIcon={<Undo2 className="h-4 w-4" />}
              disabled={busy}
              onClick={() => setConfirmUnpublish(true)}
            >
              Gỡ khỏi trang công khai
            </Button>
          )}

          <section aria-labelledby="revision-history">
            <h3 id="revision-history" className="flex items-center gap-2 font-semibold">
              <History className="h-4 w-4" aria-hidden="true" /> Lịch sử phiên bản
            </h3>
            <ol className="mt-2 flex flex-col gap-2">
              {(article.revisions ?? []).map((revision) => (
                <li key={revision.id} className="rounded-lg border border-outline-variant/40 p-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">#{revision.revisionNumber}</span>
                    <StatusBadge
                      label={REVISION_STATUS_LABELS[revision.status]}
                      variant={REVISION_VARIANT[revision.status]}
                    />
                  </div>
                  <p className="mt-1">{revision.title}</p>
                  <p className="text-xs text-on-surface-variant">
                    Tạo {formatDateTime(revision.createdAt)} · tác giả {revision.authorName}
                    {revision.reviewedAt ? ` · duyệt ${formatDateTime(revision.reviewedAt)}` : ''}
                    {revision.sourceName ? ` · nguồn ${revision.sourceName}` : ''}
                  </p>
                </li>
              ))}
            </ol>
          </section>
        </div>
      )}
      <ReasonDialog
        open={Boolean(rejecting)}
        title="Từ chối phiên bản"
        description="Người viết sẽ thấy lý do này và tạo bản sửa mới."
        noteLabel="Lý do từ chối"
        noteMinLength={5}
        confirmLabel="Từ chối"
        confirmVariant="danger"
        onClose={() => setRejecting(null)}
        onConfirm={async (_code, note) => {
          if (!article || !rejecting) return;
          await cmsAdminApi.reject(article.id, rejecting.id, note);
          setRejecting(null);
          setVersion((value) => value + 1);
          onChanged('Đã từ chối phiên bản.');
        }}
      />
      <Dialog
        open={confirmUnpublish}
        onClose={() => setConfirmUnpublish(false)}
        title="Gỡ bài viết khỏi trang công khai?"
        description="Đường dẫn công khai sẽ trả về “không còn hiển thị” (410). Các phiên bản vẫn được giữ; có thể xuất bản lại bằng bản sửa mới."
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmUnpublish(false)}>
              Hủy
            </Button>
            <Button
              variant="danger"
              disabled={busy}
              onClick={async () => {
                setConfirmUnpublish(false);
                if (article) await run(() => cmsAdminApi.unpublish(article.id), 'Đã gỡ bài viết khỏi trang công khai.');
              }}
            >
              Gỡ bài viết
            </Button>
          </>
        }
      />
    </Sheet>
  );
}

function ArticleEditor({
  mode,
  onClose,
  onSaved,
}: {
  mode: EditorMode | null;
  onClose: () => void;
  onSaved: (article: AdminArticle, message: string) => void;
}) {
  const [values, setValues] = useState<RevisionInput>(EMPTY_REVISION);
  const [slug, setSlug] = useState('');
  const [category, setCategory] = useState<ArticleCategory>('KNOWLEDGE');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setError(null);
    if (!mode) return;
    if (mode.kind === 'create') {
      setValues(EMPTY_REVISION);
      setSlug('');
      setCategory('KNOWLEDGE');
    } else if (mode.kind === 'draft') {
      setValues(fromRevision(mode.revision));
    } else {
      const base =
        mode.article.revisions?.find((revision) => revision.id === mode.article.publishedRevisionId) ??
        mode.article.revisions?.[0];
      setValues(fromRevision(base));
    }
  }, [mode]);

  const set = (key: keyof RevisionInput) => (event: { target: { value: string } }) =>
    setValues((previous) => ({ ...previous, [key]: event.target.value }));

  const save = async () => {
    if (!mode) return;
    setBusy(true);
    setError(null);
    try {
      if (mode.kind === 'create') {
        const input: ArticleInput = { ...values, slug: slug.trim(), category };
        onSaved(await cmsAdminApi.create(input), 'Đã tạo bản nháp.');
      } else if (mode.kind === 'draft') {
        onSaved(await cmsAdminApi.updateDraft(mode.article.id, mode.revision.id, values), 'Đã lưu bản nháp.');
      } else {
        onSaved(await cmsAdminApi.newRevision(mode.article.id, values), 'Đã tạo bản sửa mới (bản nháp).');
      }
    } catch (failure) {
      setError(errorMessage(failure, 'Không lưu được bài viết.'));
    } finally {
      setBusy(false);
    }
  };

  const title = mode?.kind === 'create' ? 'Bài viết mới' : mode?.kind === 'draft' ? 'Sửa bản nháp' : 'Bản sửa mới';
  return (
    <Dialog
      open={Boolean(mode)}
      onClose={onClose}
      size="lg"
      title={title}
      description="Nội dung HTML được lọc an toàn khi lưu (không script, không thuộc tính sự kiện). Ghi rõ tác giả và nguồn."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Hủy
          </Button>
          <Button onClick={save} isLoading={busy}>
            Lưu bản nháp
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        {error && (
          <InlineFeedback kind="error" title="Chưa lưu được">
            {error}
          </InlineFeedback>
        )}
        {mode?.kind === 'create' && (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              label="Đường dẫn (slug)"
              hint="Chữ thường không dấu, số, dấu gạch ngang. Không đổi được sau khi tạo."
              required
            >
              {(control) => (
                <TextInput
                  {...control}
                  value={slug}
                  onChange={(event) => setSlug(event.target.value)}
                  maxLength={200}
                />
              )}
            </FormField>
            <FormField label="Chuyên mục" required>
              {(control) => (
                <Select
                  {...control}
                  value={category}
                  options={ARTICLE_CATEGORIES}
                  onChange={(event) => setCategory(event.target.value as ArticleCategory)}
                />
              )}
            </FormField>
          </div>
        )}
        <FormField label="Tiêu đề" required>
          {(control) => <TextInput {...control} value={values.title} onChange={set('title')} maxLength={500} />}
        </FormField>
        <FormField label="Tóm tắt">
          {(control) => (
            <TextArea {...control} rows={2} value={values.summary} onChange={set('summary')} maxLength={2000} />
          )}
        </FormField>
        <FormField
          label="Nội dung (HTML)"
          required
          hint="Cho phép tiêu đề, đoạn văn, danh sách, bảng, liên kết và ảnh."
        >
          {(control) => <TextArea {...control} rows={10} value={values.contentHtml} onChange={set('contentHtml')} />}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Tác giả / ban biên tập" required>
            {(control) => (
              <TextInput {...control} value={values.authorName} onChange={set('authorName')} maxLength={255} />
            )}
          </FormField>
          <FormField label="Ảnh bìa" hint="Ảnh đã tải lên (/api/v1/public/media/…) hoặc https://">
            {(control) => <TextInput {...control} value={values.coverImageUrl} onChange={set('coverImageUrl')} />}
          </FormField>
          <FormField label="Tên nguồn">
            {(control) => (
              <TextInput {...control} value={values.sourceName} onChange={set('sourceName')} maxLength={255} />
            )}
          </FormField>
          <FormField label="Đường dẫn nguồn" hint="https://…">
            {(control) => <TextInput {...control} type="url" value={values.sourceUrl} onChange={set('sourceUrl')} />}
          </FormField>
          <FormField label="Căn cứ pháp lý">
            {(control) => (
              <TextInput {...control} value={values.legalReference} onChange={set('legalReference')} maxLength={500} />
            )}
          </FormField>
          <FormField label="Mô tả SEO" hint="Tối đa 320 ký tự; để trống sẽ dùng tóm tắt.">
            {(control) => (
              <TextInput
                {...control}
                value={values.metaDescription}
                onChange={set('metaDescription')}
                maxLength={320}
              />
            )}
          </FormField>
        </div>
      </div>
    </Dialog>
  );
}

export default CmsManagementPage;
