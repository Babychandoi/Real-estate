# Bảo vệ nhánh `main`: bắt buộc CI xanh và một review

Yêu cầu audit F01.9 (EXTERNAL: cần quyền admin repository). Tại thời điểm audit, `main` trả `protected: false` và
repository không có ruleset. Các lệnh dưới đây chưa được chạy.

## Điều kiện trước
- Tài khoản chạy lệnh có quyền **admin** trên `Babychandoi/Real-estate` và `gh auth status` hợp lệ.
- Repository riêng tư chỉ có branch protection/ruleset với gói GitHub Pro, Team hoặc Enterprise; với gói Free, API
  trả `403 Upgrade to GitHub Pro or make this repository public to enable this feature`.
- Tên check phải trùng tên job mà GitHub Actions báo cáo. Kế hoạch tách CI (S0-FE) dùng các job `backend-tests`,
  `frontend-checks`, `e2e`, `security-scan`; hiện `ci.yml` mới có `verify` và `security-scan`. Trước khi bật, lấy tên
  thật từ một lần chạy trên nhánh đã gộp CI mới:

```bash
OWNER=Babychandoi REPO=Real-estate
SHA=$(gh api "repos/$OWNER/$REPO/commits/main" --jq .sha)
gh api "repos/$OWNER/$REPO/commits/$SHA/check-runs" --jq '.check_runs[] | [.name, .app.id, .app.slug] | @tsv'
```
Nếu job có khóa `name:` trong workflow thì tên check là giá trị đó (ví dụ `Backend tests`), không phải id của job.
`app.id` của GitHub Actions dùng làm `integration_id` bên dưới (thường là `15368`), để một ứng dụng khác không thể báo
trạng thái trùng tên thay cho CI.

## Cách 1 (khuyến nghị): ruleset cho nhánh mặc định

```bash
OWNER=Babychandoi REPO=Real-estate
gh api -X POST "repos/$OWNER/$REPO/rulesets" -H "Accept: application/vnd.github+json" --input - <<'JSON'
{
  "name": "main-release-gate",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] } },
  "bypass_actors": [],
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "required_linear_history" },
    { "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 1,
        "dismiss_stale_reviews_on_push": true,
        "require_code_owner_review": false,
        "require_last_push_approval": true,
        "required_review_thread_resolution": true
      } },
    { "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": true,
        "required_status_checks": [
          { "context": "backend-tests", "integration_id": 15368 },
          { "context": "frontend-checks", "integration_id": 15368 },
          { "context": "e2e", "integration_id": 15368 },
          { "context": "security-scan", "integration_id": 15368 }
        ]
      } }
  ]
}
JSON
```

- `strict_required_status_checks_policy: true`: PR phải cập nhật với `main` mới nhất trước khi merge.
- `require_last_push_approval: true`: người push commit cuối không tự duyệt được thay đổi của mình.
- `bypass_actors` để trống: kể cả admin cũng đi qua PR. Nếu cần lối thoát khẩn cấp, thêm vai trò admin với
  `"bypass_mode": "pull_request"` (vẫn phải qua PR) và ghi lý do mỗi lần dùng.
- Khi team `@security-reviewers`, `@database-reviewers`, `@platform-reviewers` trong `.github/CODEOWNERS` đã tồn tại
  thật, đổi `require_code_owner_review` thành `true`.

Kiểm tra:
```bash
gh api "repos/$OWNER/$REPO/rulesets" --jq '.[] | [.id, .name, .enforcement] | @tsv'
gh api "repos/$OWNER/$REPO/rules/branches/main" --jq '.[].type'
```

## Cách 2: branch protection cổ điển (nếu không dùng ruleset)

```bash
OWNER=Babychandoi REPO=Real-estate
gh api -X PUT "repos/$OWNER/$REPO/branches/main/protection" -H "Accept: application/vnd.github+json" --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "checks": [
      { "context": "backend-tests", "app_id": 15368 },
      { "context": "frontend-checks", "app_id": 15368 },
      { "context": "e2e", "app_id": 15368 },
      { "context": "security-scan", "app_id": 15368 }
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "require_last_push_approval": true
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
gh api "repos/$OWNER/$REPO/branches/main/protection" --jq '{checks: .required_status_checks.checks, reviews: .required_pull_request_reviews.required_approving_review_count, admins: .enforce_admins.enabled}'
```

## Sau khi bật
- Tạo một PR thử có CI đỏ và xác nhận nút merge bị khóa; một PR xanh cần đúng một review.
- Dependabot vẫn mở PR bình thường; PR của nó cũng phải qua các check trên.
- Nếu sau này thực hiện phương án D của `docs/ops/GIT_HISTORY_REMEDIATION_PLAN.md` (tạo lại repository), phải chạy lại
  các lệnh này trên repository mới.
- Gỡ bỏ (khi thật sự cần): `gh api -X DELETE "repos/$OWNER/$REPO/rulesets/<id>"` hoặc
  `gh api -X DELETE "repos/$OWNER/$REPO/branches/main/protection"`.
