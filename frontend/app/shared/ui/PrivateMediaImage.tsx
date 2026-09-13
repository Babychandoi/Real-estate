import { useEffect, useState } from 'react';
import { ImageOff } from 'lucide-react';
import { apiFetch } from '@/shared/api/client';

interface PrivateMediaImageProps {
  src?: string;
  alt: string;
}

export function PrivateMediaImage({ src, alt }: PrivateMediaImageProps) {
  const [objectUrl, setObjectUrl] = useState('');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!src) return;
    const controller = new AbortController();
    let localUrl = '';
    setFailed(false);
    apiFetch(src, { signal: controller.signal, headers: { Accept: 'image/*' } })
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.blob();
      })
      .then((blob) => {
        localUrl = URL.createObjectURL(blob);
        setObjectUrl(localUrl);
      })
      .catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => {
      controller.abort();
      if (localUrl) URL.revokeObjectURL(localUrl);
    };
  }, [src]);

  if (!src || failed) {
    return <div className="grid aspect-[4/3] place-items-center rounded-xl bg-slate-100 text-slate-500">
      <span className="flex items-center gap-2 text-sm"><ImageOff className="h-4 w-4" />Không tải được ảnh</span>
    </div>;
  }
  if (!objectUrl) return <div className="grid aspect-[4/3] place-items-center rounded-xl bg-slate-100 text-sm text-slate-500" role="status">Đang tải ảnh bảo mật…</div>;
  return <img src={objectUrl} alt={alt} className="aspect-[4/3] w-full rounded-xl bg-slate-100 object-contain" />;
}
