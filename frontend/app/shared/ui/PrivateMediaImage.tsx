import { useEffect, useState } from 'react';
import { ImageOff } from 'lucide-react';
import { apiFetch } from '@/shared/api/client';

interface PrivateMediaImageProps {
  src?: string;
  alt: string;
  /** Short-lived grant from a password re-confirmation (staff access is also logged with a reason). */
  accessToken?: string;
  expiresAt?: string;
}

export function PrivateMediaImage({ src, alt, accessToken, expiresAt }: PrivateMediaImageProps) {
  const key = `${src ?? ''}|${accessToken ?? ''}|${expiresAt ?? ''}`;
  const [image, setImage] = useState({ key: '', url: '' });
  const objectUrl = image.key === key ? image.url : '';
  const [expired, setExpired] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!src) return;
    const controller = new AbortController();
    let localUrl = '';
    setFailed(false);
    setExpired(false);
    const deadline = expiresAt ? Date.parse(expiresAt) : Infinity;
    const isExpired = () => Number.isNaN(deadline) || Date.now() >= deadline;
    const checkExpiry = () => {
      if (!isExpired()) return;
      controller.abort();
      if (localUrl) URL.revokeObjectURL(localUrl);
      localUrl = '';
      setImage({ key, url: '' });
      setExpired(true);
    };
    if (isExpired()) {
      checkExpiry();
      return;
    }
    const timer = Number.isFinite(deadline)
      ? setTimeout(checkExpiry, Math.min(deadline - Date.now(), 2147483647))
      : undefined;
    window.addEventListener('focus', checkExpiry);
    document.addEventListener('visibilitychange', checkExpiry);
    apiFetch(src, {
      signal: controller.signal,
      headers: accessToken ? { Accept: 'image/*', 'X-Kyc-Document-Access': accessToken } : { Accept: 'image/*' },
    })
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.blob();
      })
      .then((blob) => {
        if (controller.signal.aborted) return;
        if (isExpired()) {
          checkExpiry();
          return;
        }
        localUrl = URL.createObjectURL(blob);
        setImage({ key, url: localUrl });
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      });
    return () => {
      controller.abort();
      clearTimeout(timer);
      window.removeEventListener('focus', checkExpiry);
      document.removeEventListener('visibilitychange', checkExpiry);
      if (localUrl) URL.revokeObjectURL(localUrl);
    };
  }, [src, accessToken, expiresAt, key]);

  if (!src || failed || expired) {
    return (
      <div className="grid aspect-[4/3] place-items-center rounded-xl bg-slate-100 text-slate-500">
        <span className="flex items-center gap-2 text-sm">
          <ImageOff className="h-4 w-4" />
          {expired ? 'Quyền xem ảnh đã hết hạn' : 'Không tải được ảnh'}
        </span>
      </div>
    );
  }
  if (!objectUrl)
    return (
      <div
        className="grid aspect-[4/3] place-items-center rounded-xl bg-slate-100 text-sm text-slate-500"
        role="status"
      >
        Đang tải ảnh bảo mật…
      </div>
    );
  return <img src={objectUrl} alt={alt} className="aspect-[4/3] w-full rounded-xl bg-slate-100 object-contain" />;
}
