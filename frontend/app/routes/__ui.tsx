/**
 * UI kit catalog (`/__ui`): every shared component in every state, for design review and the a11y suite.
 * Registered only when `import.meta.env.DEV` or `VITE_ENABLE_UI_CATALOG=true` (see routes.tsx), so production
 * builds do not ship it.
 */
import React, { useState } from 'react';
import { Building2, Filter, Home, Search, SlidersHorizontal, Trash2 } from 'lucide-react';
import { useDocumentMeta } from '@/shared/seo/useDocumentMeta';
import { Avatar } from '@/shared/ui/Avatar';
import { Badge } from '@/shared/ui/Badge';
import { Button, ButtonLink } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';
import { Chip, ChipGroup } from '@/shared/ui/Chip';
import { DataTable, type DataTableSort, type DataTableStatus } from '@/shared/ui/DataTable';
import { Dialog } from '@/shared/ui/Dialog';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { FormField } from '@/shared/ui/FormField';
import { Icon } from '@/shared/ui/Icon';
import { IconButton } from '@/shared/ui/IconButton';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { Money, UnitPriceText } from '@/shared/ui/Money';
import { LoadMore, Pagination } from '@/shared/ui/Pagination';
import { RadioGroup } from '@/shared/ui/Radio';
import { ResponsiveImage, type ImageDto } from '@/shared/ui/ResponsiveImage';
import { Select } from '@/shared/ui/Select';
import { Sheet } from '@/shared/ui/Sheet';
import { LoadingStatus, Skeleton, SkeletonText } from '@/shared/ui/Skeleton';
import { Switch } from '@/shared/ui/Switch';
import { Tabs } from '@/shared/ui/Tabs';
import { TextArea, TextInput } from '@/shared/ui/TextInput';
import { useToast } from '@/shared/ui/Toast';
import { TrustBadge, TrustPanel, type Trust } from '@/shared/ui/TrustBadge';

function svgImage(width: number, color: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${Math.round(width * 0.625)}"><rect width="100%" height="100%" fill="${color}"/><text x="50%" y="50%" font-size="${Math.round(width / 12)}" text-anchor="middle" fill="#ffffff" font-family="sans-serif">${width}w</text></svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

const sampleImage: ImageDto = {
  url: svgImage(1600, '#0f4c81'),
  width: 1600,
  height: 1000,
  srcset: [320, 640, 960, 1600].map((width) => ({ url: svgImage(width, '#0f4c81'), width })),
  placeholder: { dominantColor: '#c8b8a0' },
};
const brokenImage: ImageDto = { url: 'data:image/png;base64,AAAA', placeholder: { dominantColor: '#dce9ff' } };

const trustSamples: Trust = {
  identity: { status: 'VERIFIED', checkedAt: '2026-08-12T02:00:00Z', expiresAt: '2028-08-12T02:00:00Z' },
  listing: { status: 'CHECKED', checkedAt: '2026-08-30T09:30:00Z' },
  ownership: { status: 'PENDING', checkedAt: null, expiresAt: null, documentType: 'CERTIFICATE_OF_OWNERSHIP' },
};

interface SampleRow {
  id: string;
  title: string;
  status: string;
  price: number;
}
const sampleRows: SampleRow[] = [
  { id: 'r1', title: 'Căn hộ 2PN Cầu Giấy', status: 'Chờ duyệt', price: 3_950_000_000 },
  { id: 'r2', title: 'Nhà phố 4 tầng Đống Đa', status: 'Đang hiển thị', price: 12_500_000_000 },
  { id: 'r3', title: 'Căn hộ cho thuê Tây Hồ', status: 'Tạm dừng', price: 14_500_000 },
];

const COLOR_TOKENS = [
  ['primary', 'bg-primary', 'text-primary-on'],
  ['primary-container', 'bg-primary-container', 'text-primary-on'],
  ['success', 'bg-success', 'text-success-on'],
  ['warning', 'bg-warning', 'text-warning-on'],
  ['error', 'bg-error', 'text-error-on'],
  ['info-container', 'bg-info-container', 'text-info-on-container'],
  ['surface', 'bg-surface', 'text-on-surface'],
  ['surface-container', 'bg-surface-container', 'text-on-surface'],
  ['on-surface', 'bg-on-surface', 'text-surface'],
  ['on-surface-variant', 'bg-on-surface-variant', 'text-surface'],
  ['outline', 'bg-outline', 'text-surface-container-lowest'],
] as const;

const TYPE_SCALE = [
  ['text-display', 'Display 40/48'],
  ['text-display-mobile', 'H1 mobile 28/36'],
  ['text-headline-lg', 'Headline 32/40'],
  ['text-headline-md', 'Headline 24/32'],
  ['text-headline-sm', 'Headline 18/26'],
  ['text-body', 'Body 16/26 — Nhà còn thật, thông tin rõ, hẹn xem có người phản hồi.'],
  ['text-body-sm', 'Body 14/22 — Thông tin phụ, mô tả ngắn.'],
  ['text-label', 'Label 12/16 — nhãn nhỏ nhất cho thông tin có nghĩa'],
] as const;

function Section({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section
      aria-labelledby={id}
      className="rounded-panel border border-outline-variant bg-surface-container-lowest p-5 sm:p-6"
    >
      <h2 id={id} className="text-headline-md text-on-surface">
        {title}
      </h2>
      <div className="mt-4 flex flex-col gap-6">{children}</div>
    </section>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="text-body-sm font-semibold text-on-surface-variant">{title}</h3>
      <div className="mt-3 flex flex-wrap items-start gap-3">{children}</div>
    </div>
  );
}

const columns = [
  { key: 'title', header: 'Tin đăng', sortable: true, cell: (row: SampleRow) => row.title },
  { key: 'status', header: 'Trạng thái', cell: (row: SampleRow) => <Badge variant="neutral">{row.status}</Badge> },
  {
    key: 'price',
    header: 'Giá',
    sortable: true,
    align: 'end' as const,
    cell: (row: SampleRow) => (
      <Money price={{ amount: row.price, currency: 'VND', period: row.price < 1e9 ? 'MONTH' : null }} />
    ),
  },
];

const TABLE_STATES: Array<{ status: DataTableStatus; title: string; rows: SampleRow[] }> = [
  { status: 'loading', title: 'Đang tải lần đầu', rows: [] },
  { status: 'refreshing', title: 'Đang làm mới', rows: sampleRows },
  { status: 'ready', title: 'Không có dữ liệu', rows: [] },
  { status: 'error', title: 'Lỗi tải', rows: [] },
  { status: 'partial-error', title: 'Lỗi một phần', rows: sampleRows.slice(0, 2) },
  { status: 'permission-denied', title: 'Không có quyền', rows: [] },
  { status: 'conflict', title: 'Xung đột dữ liệu', rows: sampleRows },
];

export default function UiCatalogPage() {
  useDocumentMeta({ title: 'Thư viện giao diện | Nhà Đất Chuẩn', robots: 'noindex,nofollow' });
  const toast = useToast();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [switchOn, setSwitchOn] = useState(true);
  const [chips, setChips] = useState<string[]>(['APARTMENT']);
  const [purpose, setPurpose] = useState<'SALE' | 'RENT' | null>('SALE');
  const [page, setPage] = useState(3);
  const [tab, setTab] = useState<'active' | 'pending' | 'draft' | 'archived'>('active');
  const [sort, setSort] = useState<DataTableSort>({ key: 'price', direction: 'desc' });
  const [selected, setSelected] = useState<Set<string>>(new Set(['r2']));
  const [loadingMore, setLoadingMore] = useState(false);

  const toggleChip = (value: string) =>
    setChips((current) => (current.includes(value) ? current.filter((item) => item !== value) : [...current, value]));

  return (
    <main id="main-content" className="min-h-screen bg-surface px-4 py-8 sm:px-8" data-ready="true">
      <div className="mx-auto flex max-w-6xl flex-col gap-6">
        <header>
          <p className="text-label uppercase tracking-wide text-on-surface-variant">
            Nội bộ · không có trong bản production
          </p>
          <h1 className="text-display-mobile text-on-surface sm:text-headline-lg">Thư viện giao diện</h1>
          <p className="mt-2 max-w-3xl text-body text-on-surface-variant">
            Mọi thành phần dùng chung ở mọi trạng thái, dùng để rà soát thiết kế và chạy kiểm thử khả năng truy cập.
            Nguồn token: <code>app/styles/tokens.css</code>; hướng dẫn: <code>docs/design-system.md</code>.
          </p>
        </header>

        <Section id="ui-tokens" title="Màu và chữ">
          <Group title="Màu (token)">
            {COLOR_TOKENS.map(([name, bg, fg]) => (
              <div
                key={name}
                className={`flex h-20 w-36 items-end rounded-card border border-outline-variant p-2 ${bg} ${fg}`}
              >
                <span className="text-label">{name}</span>
              </div>
            ))}
          </Group>
          <Group title="Cỡ chữ">
            <div className="flex w-full flex-col gap-2">
              {TYPE_SCALE.map(([className, text]) => (
                <p key={className} className={`${className} text-on-surface`}>
                  {text}
                </p>
              ))}
            </div>
          </Group>
          <Group title="Biểu tượng Lucide 16 / 20 / 24">
            <Icon icon={Home} size="sm" />
            <Icon icon={Home} size="md" />
            <Icon icon={Home} size="lg" />
            <Icon icon={Search} size="lg" label="Tìm kiếm" />
          </Group>
        </Section>

        <Section id="ui-buttons" title="Nút">
          {(['primary', 'secondary', 'outline', 'ghost', 'danger'] as const).map((variant) => (
            <Group key={variant} title={`Biến thể ${variant}`}>
              <Button variant={variant} size="sm">
                Nhỏ 36px
              </Button>
              <Button variant={variant}>Vừa 44px</Button>
              <Button variant={variant} size="lg" leftIcon={<Search className="h-4 w-4" />}>
                Lớn 48px
              </Button>
              <Button variant={variant} disabled>
                Không khả dụng
              </Button>
              <Button variant={variant} isLoading>
                Đang gửi
              </Button>
            </Group>
          ))}
          <Group title="Liên kết dạng nút và nút biểu tượng">
            <ButtonLink to="/search" variant="outline" leftIcon={<Search className="h-4 w-4" />}>
              Mở trang tìm kiếm
            </ButtonLink>
            <IconButton icon={SlidersHorizontal} aria-label="Điều chỉnh bộ lọc" size="sm" />
            <IconButton icon={Filter} aria-label="Lọc kết quả" variant="outline" />
            <IconButton icon={Search} aria-label="Tìm kiếm" variant="primary" size="lg" />
            <IconButton icon={Trash2} aria-label="Xóa tin nháp" variant="danger" />
            <IconButton icon={Trash2} aria-label="Xóa (không khả dụng)" variant="danger" disabled />
          </Group>
        </Section>

        <Section id="ui-forms" title="Biểu mẫu">
          <div className="grid gap-5 md:grid-cols-2">
            <FormField label="Tiêu đề tin (chưa nhập)">
              {(field) => <TextInput {...field} placeholder="VD: Căn hộ 2PN" />}
            </FormField>
            <FormField label="Email liên hệ" hint="Chỉ dùng để gửi thông báo về tin đăng." required>
              {(field) => <TextInput {...field} type="email" defaultValue="chu.nha@example.invalid" />}
            </FormField>
            <FormField label="Diện tích (m²)" error="Diện tích phải lớn hơn 0." required>
              {(field) => <TextInput {...field} inputMode="decimal" defaultValue="0" />}
            </FormField>
            <FormField label="Mã tin (không sửa được)" disabled>
              {(field) => <TextInput {...field} defaultValue="UAT-2601" />}
            </FormField>
            <FormField label="Tên dự án" status="saving">
              {(field) => (
                <TextInput {...field} defaultValue="An Phú Residence" leadingIcon={<Building2 className="h-4 w-4" />} />
              )}
            </FormField>
            <FormField label="Địa chỉ" status="saved" hint="Tự lưu bản nháp.">
              {(field) => <TextInput {...field} size="lg" defaultValue="Dịch Vọng, Cầu Giấy, Hà Nội" />}
            </FormField>
            <FormField label="Loại hình">
              {(field) => (
                <Select
                  {...field}
                  placeholder="Chọn loại hình"
                  defaultValue="APARTMENT"
                  options={[
                    { value: 'APARTMENT', label: 'Căn hộ' },
                    { value: 'HOUSE', label: 'Nhà riêng' },
                    { value: 'LAND', label: 'Đất', disabled: true },
                  ]}
                />
              )}
            </FormField>
            <FormField label="Mô tả" hint="Không ghi số điện thoại hoặc email trong mô tả.">
              {(field) => <TextArea {...field} rows={3} defaultValue="Căn hộ hai phòng ngủ, ban công thoáng." />}
            </FormField>
          </div>
          <div className="grid gap-5 md:grid-cols-3">
            <div>
              <Checkbox label="Nhận thông báo tin mới" description="Tối đa một email mỗi ngày." defaultChecked />
              <Checkbox label="Chỉ tin có ảnh" />
              <Checkbox label="Chọn một phần" indeterminate />
              <Checkbox label="Không khả dụng" disabled />
            </div>
            <RadioGroup
              legend="Nhu cầu"
              name="catalog-purpose"
              value={purpose}
              onChange={setPurpose}
              options={[
                { value: 'SALE', label: 'Mua bán' },
                { value: 'RENT', label: 'Cho thuê', description: 'Giá tính theo tháng' },
              ]}
            />
            <RadioGroup
              legend="Hướng nhà"
              name="catalog-direction"
              value={null}
              onChange={() => undefined}
              error="Chọn một hướng nhà."
              required
              options={[
                { value: 'EAST', label: 'Đông' },
                { value: 'WEST', label: 'Tây' },
              ]}
            />
          </div>
          <div className="flex max-w-md flex-col divide-y divide-outline-variant">
            <Switch label="Hiển thị tin công khai" checked={switchOn} onCheckedChange={setSwitchOn} />
            <Switch label="Tắt" description="Áp dụng ngay khi bật." checked={false} onCheckedChange={() => undefined} />
            <Switch label="Không khả dụng" checked disabled onCheckedChange={() => undefined} />
          </div>
          <ChipGroup label="Loại hình bất động sản">
            <Chip selected={chips.includes('APARTMENT')} onClick={() => toggleChip('APARTMENT')} icon={Building2}>
              Căn hộ
            </Chip>
            <Chip selected={chips.includes('HOUSE')} onClick={() => toggleChip('HOUSE')} icon={Home}>
              Nhà riêng
            </Chip>
            <Chip selected={chips.includes('SMALL')} onClick={() => toggleChip('SMALL')} size="sm">
              Dưới 50 m²
            </Chip>
            <Chip selected={false} disabled>
              Không khả dụng
            </Chip>
          </ChipGroup>
        </Section>

        <Section id="ui-status" title="Nhãn, xác minh và phản hồi">
          <Group title="Badge">
            {(['neutral', 'primary', 'success', 'warning', 'error', 'info', 'verified', 'vip'] as const).map(
              (variant) => (
                <Badge key={variant} variant={variant}>
                  {variant}
                </Badge>
              ),
            )}
          </Group>
          <Group title="TrustBadge — danh tính người đăng">
            {(['VERIFIED', 'PENDING', 'EXPIRED', 'REJECTED', 'NOT_SUBMITTED'] as const).map((status) => (
              <TrustBadge key={status} kind="identity" status={status} />
            ))}
          </Group>
          <Group title="TrustBadge — kiểm duyệt nội dung tin">
            <TrustBadge kind="listing" status="CHECKED" />
            <TrustBadge kind="listing" status="NOT_CHECKED" />
          </Group>
          <Group title="TrustBadge — giấy tờ chủ sở hữu">
            {(['VERIFIED', 'PENDING', 'REVOKED', 'EXPIRED', 'REJECTED', 'NOT_SUBMITTED'] as const).map((status) => (
              <TrustBadge key={status} kind="ownership" status={status} />
            ))}
          </Group>
          <div className="grid gap-4 md:grid-cols-2">
            <TrustPanel trust={trustSamples} />
            <TrustBadge
              kind="ownership"
              status="EXPIRED"
              checkedAt="2026-02-01T00:00:00Z"
              expiresAt="2026-08-01T00:00:00Z"
              detailed
            />
          </div>
          <div className="grid gap-3">
            <InlineFeedback kind="success" title="Đã gửi tin để kiểm duyệt">
              Chúng tôi sẽ thông báo kết quả qua email.
            </InlineFeedback>
            <InlineFeedback
              kind="error"
              title="Không lưu được bản nháp"
              action={{ label: 'Thử lại', onClick: () => undefined }}
            >
              Kết nối bị gián đoạn; nội dung vẫn còn trên máy của bạn.
            </InlineFeedback>
            <InlineFeedback
              kind="conflict"
              title="Tin vừa được cập nhật ở nơi khác"
              action={{ label: 'Tải lại', onClick: () => undefined }}
            >
              Tải lại để xem bản mới nhất trước khi sửa tiếp.
            </InlineFeedback>
            <InlineFeedback kind="offline" title="Bạn đang ngoại tuyến">
              Thay đổi sẽ được gửi khi có mạng trở lại.
            </InlineFeedback>
            <InlineFeedback kind="info" title="Giá thuê tính theo tháng" />
            <InlineFeedback kind="warning" title="Tin sắp hết hạn hiển thị" />
          </div>
          <Group title="Toast (bấm để hiện)">
            <Button variant="outline" onClick={() => toast.show({ kind: 'success', title: 'Đã lưu tìm kiếm' })}>
              Thành công
            </Button>
            <Button
              variant="outline"
              onClick={() =>
                toast.show({
                  kind: 'error',
                  title: 'Không gửi được yêu cầu',
                  description: 'Vui lòng thử lại sau ít phút.',
                  action: { label: 'Thử lại', onClick: () => undefined },
                })
              }
            >
              Lỗi có thử lại
            </Button>
            <Button variant="outline" onClick={() => toast.show({ kind: 'conflict', title: 'Dữ liệu đã thay đổi' })}>
              Xung đột
            </Button>
            <Button variant="outline" onClick={() => toast.show({ kind: 'offline', title: 'Mất kết nối mạng' })}>
              Ngoại tuyến
            </Button>
          </Group>
        </Section>

        <Section id="ui-content" title="Giá, ảnh và trạng thái nội dung">
          <Group title="Money">
            <Money
              className="text-headline-sm text-primary"
              price={{ amount: 3_950_000_000, currency: 'VND', period: null }}
            />
            <Money
              className="text-headline-sm text-primary"
              price={{ amount: 850_000_000, currency: 'VND', period: null }}
            />
            <Money
              className="text-headline-sm text-primary"
              price={{ amount: 14_500_000, currency: 'VND', period: 'MONTH' }}
            />
            <Money price={{ amount: 3_950_000_000, currency: 'VND' }} compact={false} />
            <UnitPriceText unitPrice={{ amount: 48_170_000, per: 'M2' }} className="text-on-surface-variant" />
            <Money price={null} />
          </Group>
          <Group title="ResponsiveImage: có srcset · ảnh cũ chỉ có URL · lỗi tải · chưa có ảnh">
            <ResponsiveImage
              image={sampleImage}
              alt="Ảnh mẫu với các cỡ 320–1600"
              sizes="(min-width: 768px) 240px, 100vw"
              aspectRatio="16 / 10"
              className="w-60 rounded-card"
            />
            <ResponsiveImage
              image={{ url: svgImage(640, '#006c4a') }}
              alt="Ảnh mẫu chỉ có URL gốc"
              aspectRatio="16 / 10"
              className="w-60 rounded-card"
            />
            <ResponsiveImage
              image={brokenImage}
              alt="Ảnh phòng khách"
              aspectRatio="16 / 10"
              className="w-60 rounded-card"
            />
            <ResponsiveImage image={null} alt="" aspectRatio="16 / 10" className="w-60 rounded-card" />
          </Group>
          <Group title="Avatar">
            {(['xs', 'sm', 'md', 'lg', 'xl'] as const).map((size) => (
              <Avatar key={size} name="Nguyễn Minh Tuấn" size={size} />
            ))}
            <Avatar name="Trần Thu Hà" src={svgImage(160, '#733c00')} size="lg" />
            <Avatar name="Lê Quang Huy" src="data:image/png;base64,AAAA" size="lg" />
            <Avatar size="lg" />
          </Group>
          <div className="grid gap-4 md:grid-cols-2">
            <LoadingStatus
              label="Đang tải danh sách tin…"
              className="flex flex-col gap-3 rounded-card border border-outline-variant p-4"
            >
              <Skeleton className="aspect-[16/10] w-full" />
              <SkeletonText lines={3} />
            </LoadingStatus>
            <EmptyState
              title="Chưa có tin phù hợp"
              description="Thử bỏ bớt bộ lọc hoặc mở rộng khu vực tìm kiếm."
              actions={
                <ButtonLink to="/search" variant="outline">
                  Xóa bộ lọc
                </ButtonLink>
              }
            />
            <ErrorState onRetry={() => undefined} />
            <ErrorState
              title="Không mở được bản đồ"
              description="Trình duyệt chưa tải được bản đồ."
              retrying
              onRetry={() => undefined}
            />
          </div>
        </Section>

        <Section id="ui-navigation" title="Điều hướng">
          <Tabs
            label="Trạng thái tin"
            value={tab}
            onChange={setTab}
            items={[
              {
                id: 'active',
                label: 'Đang hiển thị',
                count: 12,
                content: <p className="text-body-sm">12 tin đang hiển thị.</p>,
              },
              {
                id: 'pending',
                label: 'Chờ duyệt',
                count: 2,
                content: <p className="text-body-sm">2 tin chờ duyệt.</p>,
              },
              { id: 'draft', label: 'Bản nháp', content: <p className="text-body-sm">Chưa có bản nháp.</p> },
              { id: 'archived', label: 'Đã lưu trữ', disabled: true, content: null },
            ]}
          />
          <Pagination page={page} pageCount={12} onPageChange={setPage} label="Phân trang mẫu" />
          <Pagination page={2} hasNext onPageChange={() => undefined} label="Phân trang không biết tổng" />
          <div className="grid gap-6 md:grid-cols-2">
            <LoadMore
              loadedCount={24}
              hasNext
              loading={loadingMore}
              onLoadMore={() => {
                setLoadingMore(true);
                window.setTimeout(() => setLoadingMore(false), 800);
              }}
              total={{ value: 10_000, relation: 'gte' }}
              noun="tin"
              label="Xem thêm tin"
            />
            <LoadMore
              loadedCount={48}
              hasNext
              loading={false}
              onLoadMore={() => undefined}
              error="Không tải được trang tiếp theo."
              noun="tin"
            />
            <LoadMore
              loadedCount={36}
              hasNext
              loading
              onLoadMore={() => undefined}
              total={{ value: 120, relation: 'eq' }}
              noun="tin"
            />
            <LoadMore loadedCount={12} hasNext={false} loading={false} onLoadMore={() => undefined} noun="tin" />
          </div>
        </Section>

        <Section id="ui-overlays" title="Hộp thoại và bảng trượt">
          <Group title="Mở để kiểm tra bẫy tiêu điểm, Esc và trả tiêu điểm">
            <Button onClick={() => setDialogOpen(true)}>Mở hộp thoại</Button>
            <Button
              variant="outline"
              leftIcon={<SlidersHorizontal className="h-4 w-4" />}
              onClick={() => setSheetOpen(true)}
            >
              Mở bảng bộ lọc
            </Button>
          </Group>
          <Dialog
            open={dialogOpen}
            onClose={() => setDialogOpen(false)}
            title="Báo cáo tin vi phạm"
            description="Mô tả ngắn giúp đội kiểm duyệt xử lý nhanh hơn."
            footer={
              <>
                <Button variant="ghost" onClick={() => setDialogOpen(false)}>
                  Hủy
                </Button>
                <Button onClick={() => setDialogOpen(false)}>Gửi báo cáo</Button>
              </>
            }
          >
            <FormField label="Nội dung báo cáo" hint="Tối thiểu 10 ký tự." required>
              {(field) => <TextArea {...field} rows={4} />}
            </FormField>
          </Dialog>
          <Sheet
            open={sheetOpen}
            onClose={() => setSheetOpen(false)}
            title="Bộ lọc"
            footer={
              <>
                <Button variant="ghost" onClick={() => setSheetOpen(false)}>
                  Xóa lọc
                </Button>
                <Button className="flex-1" onClick={() => setSheetOpen(false)}>
                  Xem kết quả
                </Button>
              </>
            }
          >
            <ChipGroup label="Loại hình trong bộ lọc">
              <Chip selected>Căn hộ</Chip>
              <Chip selected={false}>Nhà riêng</Chip>
            </ChipGroup>
          </Sheet>
        </Section>

        <Section id="ui-table" title="Bảng dữ liệu (phân trang, lọc, sắp xếp phía máy chủ)">
          <DataTable
            caption="Tin đăng — sắp xếp và chọn nhiều"
            captionVisible
            columns={columns}
            rows={sampleRows}
            getRowId={(row) => row.id}
            sort={sort}
            onSortChange={setSort}
            selection={{ selectedIds: selected, onChange: setSelected, rowLabel: (row) => row.title }}
            footer={<Pagination page={1} pageCount={3} onPageChange={() => undefined} label="Phân trang bảng tin" />}
          />
          {TABLE_STATES.map((state) => (
            <DataTable
              key={state.title}
              caption={`Tin đăng — ${state.title}`}
              captionVisible
              columns={columns}
              rows={state.rows}
              getRowId={(row) => row.id}
              status={state.status}
              onRetry={() => undefined}
              onReload={() => undefined}
              skeletonRows={2}
            />
          ))}
        </Section>
      </div>
    </main>
  );
}
