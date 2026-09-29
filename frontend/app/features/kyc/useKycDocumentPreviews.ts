import { useEffect, useState } from 'react';
import { apiFetch } from '@/shared/api/client';

type Access = { token: string; expiresAt: string };
type Documents = Record<string, string>;

/** Private blob URLs live only for the current, unexpired document grant. */
export function useKycDocumentPreviews(
  access: Access | null,
  documents: Documents | null,
): {
  previews: Record<string, string>;
  error: string;
  expired: boolean;
} {
  const [state, setState] = useState<{
    access: Access | null;
    previews: Record<string, string>;
    error: string;
    expired: boolean;
  }>({ access: null, previews: {}, error: '', expired: false });

  useEffect(() => {
    if (!access || !documents) return;
    const controller = new AbortController();
    const urls = new Set<string>();
    const expires = Date.parse(access.expiresAt);
    let active = true;
    const revoke = () => {
      urls.forEach((url) => URL.revokeObjectURL(url));
      urls.clear();
    };
    const expired = () => !Number.isFinite(expires) || Date.now() >= expires;
    const checkExpiry = () => {
      if (!active || !expired()) return;
      controller.abort();
      revoke();
      setState({ access, previews: {}, error: '', expired: true });
    };
    if (expired()) {
      checkExpiry();
      return;
    }
    const timer = setTimeout(checkExpiry, Math.min(expires - Date.now(), 2147483647));
    window.addEventListener('focus', checkExpiry);
    document.addEventListener('visibilitychange', checkExpiry);
    setState({ access, previews: {}, error: '', expired: false });
    void Promise.all(
      Object.entries(documents).map(async ([field, path]) => {
        const response = await apiFetch(path, {
          signal: controller.signal,
          headers: { 'X-Kyc-Document-Access': access.token, Accept: 'image/*' },
        });
        if (!response.ok) throw new Error('image unavailable');
        const blob = await response.blob();
        if (!active || controller.signal.aborted || expired()) return null;
        const url = URL.createObjectURL(blob);
        urls.add(url);
        return [field, url] as const;
      }),
    )
      .then((entries) => {
        if (!active || controller.signal.aborted) return;
        if (expired()) {
          checkExpiry();
          return;
        }
        setState({
          access,
          previews: Object.fromEntries(entries.filter((item) => item !== null)),
          error: '',
          expired: false,
        });
      })
      .catch(() => {
        if (!active || controller.signal.aborted) return;
        controller.abort();
        revoke();
        setState({
          access,
          previews: {},
          expired: false,
          error: 'Không thể tải ảnh định danh. Hãy xác nhận lại mật khẩu để thử lại.',
        });
      });
    return () => {
      active = false;
      controller.abort();
      clearTimeout(timer);
      window.removeEventListener('focus', checkExpiry);
      document.removeEventListener('visibilitychange', checkExpiry);
      revoke();
    };
  }, [access, documents]);

  return state.access === access && documents ? state : { previews: {}, error: '', expired: false };
}
