import { useState, type FormEvent } from 'react';
import { Mail, Send } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { FormField } from '@/shared/ui/FormField';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { TextInput } from '@/shared/ui/TextInput';
import { formatWait, RESEND_COOLDOWN_SECONDS, sendEmailRequest, useCooldown } from './emailRequests';

/** New verification link; neutral answer whatever the address, with a cooldown between sends. */
export function ResendVerificationForm() {
  const [email, setEmail] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const [wait, startWait] = useCooldown();

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError('');
    setSending(true);
    const result = await sendEmailRequest('/auth/resend-verification', email.trim());
    setSending(false);
    if (result.ok) {
      setSent(true);
      startWait(RESEND_COOLDOWN_SECONDS);
    } else if (result.retryAfterSeconds) {
      startWait(result.retryAfterSeconds);
      setError(`Bạn đã yêu cầu nhiều lần. Hãy thử lại sau ${formatWait(result.retryAfterSeconds)}.`);
    } else {
      setError('Chưa gửi được yêu cầu. Kiểm tra kết nối mạng rồi thử lại.');
    }
  };

  return (
    <form onSubmit={submit} className="space-y-3 text-left">
      <FormField label="Email đã đăng ký" required>
        {(control) => (
          <TextInput
            {...control}
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            leadingIcon={<Mail className="h-4 w-4" />}
          />
        )}
      </FormField>
      {sent && (
        <InlineFeedback kind="success" title="Đã tiếp nhận yêu cầu">
          Nếu email này đang chờ xác minh, một liên kết mới đã được gửi. Liên kết cũ không còn dùng được.
        </InlineFeedback>
      )}
      {error && <InlineFeedback kind="error" title={error} />}
      <Button
        type="submit"
        className="w-full"
        isLoading={sending}
        disabled={wait > 0}
        leftIcon={<Send className="h-4 w-4" />}
      >
        {wait > 0 ? `Gửi lại sau ${formatWait(wait)}` : sent ? 'Gửi lại liên kết' : 'Gửi liên kết xác minh mới'}
      </Button>
    </form>
  );
}
