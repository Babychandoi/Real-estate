import { useEffect, useState } from 'react';

/**
 * QR code for an authenticator's `otpauth://` link. Drawn in the browser only: the TOTP secret never goes to a
 * third-party QR service, and the `uqr` library is a separate chunk that loads only on this enrolment step.
 */
export default function TotpQrCode({ uri }: { uri: string }) {
  const [dataUrl, setDataUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setDataUrl(null);
    setFailed(false);
    import('uqr')
      .then(({ renderSVG }) => renderSVG(uri, { ecc: 'M', border: 2 }))
      .then((svg) => `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`)
      .then((url) => {
        if (!cancelled) setDataUrl(url);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [uri]);

  if (failed) {
    return (
      <p role="status" className="text-label text-on-surface-variant">
        Không tạo được mã QR. Hãy nhập khóa bên dưới.
      </p>
    );
  }
  if (!dataUrl) return <div aria-hidden="true" className="h-52 w-52 animate-pulse rounded-card bg-surface-container" />;
  return (
    <img
      src={dataUrl}
      width={208}
      height={208}
      alt="Mã QR thiết lập ứng dụng xác thực. Nếu không quét được, nhập khóa thiết lập bên dưới."
      className="rounded-card border border-outline-variant bg-white"
    />
  );
}
