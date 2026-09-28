# Brief S5-SEC phase B (W4) — admin MFA, sessions, session tests, least privilege, token pages

Branch `audit/s5b-sec` (from `audit-2026-09-27` at `58ea814`); Flyway **V087–V089** (the contract's V066–V067 are
unusable: `out-of-order` is off and V085 exists); backend test port 18120; Vite 5320. Parallel in W4: S7-SEO, S8-ANALYTICS.

Read first: `00_PLAN.md`, `01_REQUIREMENTS.md`, `02_CONTRACTS.md`, `03_AGENT_RULES.md`, `briefs/wave-rules.md`,
`streams/s5-sec-a.md` (ADR decisions for phase B), the other stream reports (follow-ups for S5), `docs/adr/0001-session-model.md`.
Audit: F20, §5 rows `/verify-email` `/forgot-password` `/reset-password`, `/2026/nhadatchuan/admin/login`, Admin `/users`,
§6 seeker flow (return to the original intent after verification).

Requirement IDs: F20.2, F20.3, F20.4, UI-14, UI-17, DS-11 (verification part), F05.4 (alert rules), D-14 follow-ups
(alert rules requested by S0-BE, S2, S6), rate-limit follow-ups (S1 signed media, new S5 endpoints).

## Backend
1. **Session metadata + staff TTL (V087).** `auth_sessions` gains `last_seen_at`, `idle_timeout_seconds`, `device_label`
   (browser/OS summary, never the raw UA), `ip_hint` (IPv4 /24, IPv6 /48 — no full address), `mfa_verified_at`.
   ADMIN/MODERATOR: 8 h absolute + 30 min idle; others 12 h, no idle (configurable `app.security.session.*`). Token lookup
   refuses idle-expired sessions; `last_seen_at` is touched at most once a minute (no write per request).
2. **Session management.** `GET /api/v1/me/sessions` (current flagged, newest first, bounded), `DELETE /api/v1/me/sessions/{id}`
   (only own), `POST /api/v1/me/sessions/revoke-others`; `POST /api/v1/me/password` (current password required, revokes
   every other session, notification mail via `MailOutbox`); reset password, role change, lock and MFA reset revoke all
   sessions of the target. Admin: `POST /api/v1/admin/users/{id}/sessions/revoke` and `/mfa/reset` with reason in
   `user_admin_actions`.
3. **Admin MFA (V088).** TOTP RFC 6238 (SHA-1, 6 digits, 30 s, ±1 step), secret sealed with `PiiProtectionService.seal`
   (purpose-bound AES-GCM), replay refused (`last_used_step`), 10 single-use recovery codes stored as SHA-256.
   `POST /auth/admin/login` answers `{mfaRequired, mfaState: VERIFY|ENROLL, challengeToken, challengeExpiresAt}` for
   staff instead of a session; `POST /auth/admin/mfa/enroll` (secret + otpauth URI for the challenge),
   `/auth/admin/mfa/enroll/confirm` (code → recovery codes + session), `/auth/admin/mfa/verify` (code or recovery code →
   session). Challenge: 5 min, hashed, single use, 5 wrong codes burn it. `app.security.mfa.required` (default true;
   refused false when `app.mode=production`). Self-service: `GET /api/v1/me/mfa`, regenerate recovery codes with a code.
4. **Security events (V088).** `auth_security_events` (login ok/failed for known accounts, MFA ok/failed, recovery code
   used, enrollment, password change/reset, session revoked, MFA reset) with ip hint/device only; `GET /api/v1/me/security-events`;
   metric `bds.auth.events{type,outcome}`.
5. **Token pages (V089).** Single-use expiring tokens already exist; add machine codes `TOKEN_INVALID|TOKEN_EXPIRED|TOKEN_USED`,
   `POST /auth/verify-email` (body token, GET kept for old links), `POST /auth/token-status` (reset link state before the form),
   verification `return_path` (validated relative path from registration) returned after verification (DS-11). Neutral
   answers for forgot/resend unchanged; UI throttles resend with a cooldown and honours `Retry-After`.
6. **Least privilege (F20.4).** Review every `SecurityConfig` rule and `@PreAuthorize`; `docs/security/ACCESS_MATRIX.md`;
   `AccessMatrixTests` enumerates every handler mapping: anonymous gets 401 except an explicit public allowlist, a USER
   gets 403 on admin/staff routes, MODERATOR gets 403 on ADMIN-only routes.
7. **Rate limits** for the new endpoints (MFA verify/enroll per IP, password change/recovery codes per account,
   token-status, verify-email POST) and S1's signed media endpoints. **Alert rules** requested by S0-BE/S2/S6 plus MFA
   failure spikes, with promtool unit tests.

## Frontend (new UI design: `ndc-*` shell classes, tokens, kit Button/Dialog/FormField; Vietnamese copy)
8. Admin login: password → MFA step (code or recovery code) or enrollment (secret, otpauth link, confirm, show recovery
   codes once with copy/download and explicit acknowledgement). Admin "Bảo mật tài khoản" page (MFA status, regenerate
   recovery codes, sessions, events). Account page: sessions + revoke, change password, recent security events.
   Admin users: reset MFA / revoke sessions with reason.
9. `/verify-email`, `/forgot-password`, `/reset-password`: token removed from the address bar, states valid/expired/used/
   invalid, resend with cooldown, neutral copy, return to the original page after sign-in.
10. Vitest for the MFA step, token page states and sessions panel.

## Tests (PostgreSQL + Redis, `@BdsIntegrationTest`)
Logout/revoke/expiry (absolute and idle, staff vs user), reset/password change/role change revoke, revoke one session only
affects it, MFA enrolment/verify/replay/wrong-code burn/recovery single use/required flag, challenge cannot be used as
bearer, token status codes, return path validation, access matrix, rate-limit policy resolution.

Deliverables per `03_AGENT_RULES.md`: commits, `streams/s5b-sec.md` (production notes: env vars, MFA rollout for existing
admins), final message.
