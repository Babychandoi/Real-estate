import React, { useEffect, useRef, useState } from 'react';
import { ArrowRight, CheckCircle2, Lock, Phone, User, X } from 'lucide-react';
import { apiClient } from '@/shared/api/client';
import { formatPriceVnd } from '@/entities/listing/model/types';

interface Props {
  isOpen: boolean; onClose: () => void;
  listing: { id: string; title: string; priceVnd: number; areaM2: number; address: string; imageUrl?: string; };
}
interface LeadResult { requestCode: string; status: string; createdAt: string; }

export const LeadConsultationModal: React.FC<Props> = ({ isOpen, onClose, listing }) => {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [note, setNote] = useState('');
  const [consent, setConsent] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<LeadResult | null>(null);
  const [error, setError] = useState('');
  const idempotencyKeyRef = useRef(crypto.randomUUID());

  useEffect(() => {
    if (!isOpen) return;
    const previous = document.activeElement as HTMLElement | null;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeRef.current?.focus();
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const items = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]),input:not([disabled]),textarea:not([disabled])'));
      if (!items.length) return;
      const first = items[0]; const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', handleKey);
    return () => { document.removeEventListener('keydown', handleKey); document.body.style.overflow = oldOverflow; previous?.focus(); };
  }, [isOpen, onClose]);

  if (!isOpen) return null;
  const close = () => { setResult(null); setError(''); idempotencyKeyRef.current = crypto.randomUUID(); onClose(); };
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setError('');
    if (!consent) { setError('Bạn cần đồng ý cho phép xử lý thông tin liên hệ.'); return; }
    setIsSubmitting(true);
    try {
      const created = await apiClient<LeadResult>('/public/leads', { method: 'POST', headers: { 'Idempotency-Key': idempotencyKeyRef.current }, body: JSON.stringify({
        listingId: listing.id, fullName: fullName.trim(), phone: phone.replace(/\s/g, ''), note: note.trim(), consentPolicy: consent,
      }) });
      if (!created.requestCode) throw new Error('Máy chủ không trả mã yêu cầu');
      setResult(created);
    } catch (caught) {
      const detail = caught && typeof caught === 'object' && 'problem' in caught
        ? (caught as { problem?: { detail?: string } }).problem?.detail : undefined;
      setError(detail || 'Chưa gửi được yêu cầu. Dữ liệu vẫn được giữ; vui lòng kiểm tra mạng rồi thử lại.');
    } finally { setIsSubmitting(false); }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) close(); }}>
      <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="lead-title" className="w-full max-w-lg bg-surface rounded-2xl shadow-2xl overflow-hidden max-h-[92dvh] flex flex-col">
        <div className="p-5 flex items-center justify-between bg-surface-container-low border-b border-outline-variant/30">
          <div><h2 id="lead-title" className="font-bold text-lg">Hẹn xem bất động sản</h2><p className="text-sm text-on-surface-variant">Thông tin được gửi tới người phụ trách tin đăng.</p></div>
          <button ref={closeRef} type="button" onClick={close} aria-label="Đóng hộp thoại" className="min-w-11 min-h-11 rounded-lg grid place-items-center hover:bg-surface-container focus-visible:ring-2 focus-visible:ring-primary"><X className="w-5 h-5" /></button>
        </div>
        <div className="p-5 overflow-y-auto">
          {result ? (
            <div className="py-6 text-center" aria-live="polite">
              <CheckCircle2 className="w-14 h-14 text-secondary mx-auto" />
              <h3 className="font-bold text-xl mt-3">Yêu cầu đã được ghi nhận</h3>
              <p className="text-sm text-on-surface-variant mt-2">Mã yêu cầu: <strong className="text-on-surface">{result.requestCode}</strong>. Người phụ trách sẽ liên hệ khi tiếp nhận.</p>
              <button onClick={close} className="mt-6 min-h-11 px-5 rounded-lg bg-primary text-white font-semibold">Hoàn tất</button>
            </div>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <div className="flex gap-3 p-3 bg-surface-container-low rounded-xl">
                {listing.imageUrl && <img src={listing.imageUrl} alt="" className="w-20 h-16 object-cover rounded-lg" />}
                <div className="min-w-0"><p className="font-semibold truncate">{listing.title}</p><p className="text-sm text-primary font-bold">{formatPriceVnd(listing.priceVnd)} · {listing.areaM2} m²</p><p className="text-xs text-on-surface-variant truncate">{listing.address}</p></div>
              </div>
              {error && <div id="lead-error" role="alert" className="p-3 rounded-lg bg-rose-50 text-rose-800 text-sm">{error}</div>}
              <label className="block text-sm font-semibold">Họ và tên <span aria-hidden="true">*</span><span className="relative block mt-1"><User className="absolute left-3 top-3 w-4 h-4 text-outline" /><input required autoComplete="name" value={fullName} onChange={(e) => setFullName(e.target.value)} aria-describedby={error ? 'lead-error' : undefined} className="w-full min-h-11 pl-10 pr-3 rounded-lg border border-outline-variant bg-surface focus:ring-2 focus:ring-primary" /></span></label>
              <label className="block text-sm font-semibold">Số điện thoại <span aria-hidden="true">*</span><span className="relative block mt-1"><Phone className="absolute left-3 top-3 w-4 h-4 text-outline" /><input required type="tel" inputMode="tel" autoComplete="tel" pattern="(0|\+84)[35789][0-9]{8}" value={phone} onChange={(e) => setPhone(e.target.value)} className="w-full min-h-11 pl-10 pr-3 rounded-lg border border-outline-variant bg-surface focus:ring-2 focus:ring-primary" /></span></label>
              <label className="block text-sm font-semibold">Thời gian hoặc lời nhắn <span className="font-normal text-on-surface-variant">(không bắt buộc)</span><textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} className="mt-1 w-full p-3 rounded-lg border border-outline-variant bg-surface focus:ring-2 focus:ring-primary" /></label>
              <label className="flex items-start gap-3 text-sm text-on-surface-variant"><input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} className="checkbox checkbox-primary mt-0.5" /><span>Tôi đồng ý gửi tên và số điện thoại cho người phụ trách tin này để được liên hệ.</span></label>
              <div className="flex items-center justify-between gap-3 pt-2"><span className="text-xs text-on-surface-variant flex items-center gap-1"><Lock className="w-4 h-4" /> Dữ liệu được mã hóa khi lưu</span><button type="submit" disabled={isSubmitting} className="min-h-11 px-5 rounded-lg bg-primary text-white font-semibold inline-flex items-center gap-2 disabled:opacity-50">{isSubmitting ? 'Đang gửi…' : 'Gửi yêu cầu'}<ArrowRight className="w-4 h-4" /></button></div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
