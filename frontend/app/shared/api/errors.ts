/**
 * Message of a thrown value when it carries one (an `Error`, including `ApiProblemException`), otherwise the
 * caller's fallback. Replaces `catch (err: any) { err.message || fallback }` without the `any`.
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const { message } = error as { message?: unknown };
    if (typeof message === 'string' && message) return message;
  }
  return fallback;
}
