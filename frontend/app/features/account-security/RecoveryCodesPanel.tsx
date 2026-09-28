import { useEffect, useMemo, useState } from 'react';
import { Check, Copy, Download, KeyRound } from 'lucide-react';
import { Button, buttonClasses } from '@/shared/ui/Button';
import { Checkbox } from '@/shared/ui/Checkbox';

/**
 * Shown exactly once after enrolment or regeneration: the codes are only stored hashed, so they cannot be shown
 * again. The person copies or downloads them and confirms before continuing.
 */
export function RecoveryCodesPanel({
  codes,
  onDone,
  doneLabel = 'Tiếp tục',
}: {
  codes: string[];
  onDone: () => void;
  doneLabel?: string;
}) {
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState(false);
  const text = useMemo(
    () =>
      [
        'Mã khôi phục xác thực hai lớp — Nhà Đất Chuẩn',
        'Mỗi mã chỉ dùng được một lần. Cất ở nơi an toàn, không chia sẻ.',
        '',
        ...codes,
      ].join('\n'),
    [codes],
  );
  // Object URLs are missing in some embedded browsers (and jsdom); the copy button still works there.
  const downloadUrl = useMemo(
    () =>
      typeof URL.createObjectURL === 'function'
        ? URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
        : null,
    [text],
  );
  useEffect(
    () => () => {
      if (downloadUrl) URL.revokeObjectURL(downloadUrl);
    },
    [downloadUrl],
  );

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <section aria-labelledby="recovery-codes-title" className="space-y-4">
      <div className="flex items-start gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-warning-container text-warning-on-container">
          <KeyRound className="h-5 w-5" aria-hidden="true" />
        </span>
        <div>
          <h2 id="recovery-codes-title" className="text-headline-sm font-bold text-on-surface">
            Lưu mã khôi phục
          </h2>
          <p className="mt-1 text-body-sm text-on-surface-variant">
            Dùng một mã khi không mở được ứng dụng xác thực. Mỗi mã chỉ dùng một lần và sẽ không hiển thị lại.
          </p>
        </div>
      </div>
      <ol
        className="grid grid-cols-2 gap-2 rounded-card border border-outline-variant bg-surface-container-low p-3 font-mono text-body-sm text-on-surface"
        aria-label="Danh sách mã khôi phục"
      >
        {codes.map((code) => (
          <li key={code} className="rounded bg-surface-container-lowest px-2 py-1.5 text-center tracking-wider">
            {code}
          </li>
        ))}
      </ol>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          onClick={() => void copy()}
          leftIcon={copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
        >
          {copied ? 'Đã sao chép' : 'Sao chép'}
        </Button>
        {downloadUrl && (
          <a
            href={downloadUrl}
            download="ma-khoi-phuc-nha-dat-chuan.txt"
            className={buttonClasses({ variant: 'outline' })}
          >
            <Download className="h-4 w-4" aria-hidden="true" />
            Tải tệp .txt
          </a>
        )}
      </div>
      <Checkbox
        checked={saved}
        onChange={(event) => setSaved(event.target.checked)}
        label="Tôi đã lưu các mã này ở nơi an toàn"
      />
      <Button className="w-full" disabled={!saved} onClick={onDone}>
        {doneLabel}
      </Button>
    </section>
  );
}
