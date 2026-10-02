# Bảo vệ nhánh `main`: bắt buộc CI xanh và một review

Yêu cầu audit F01.9 (EXTERNAL: cần quyền admin repository). Tại thời điểm audit, `main` trả `protected: false` và
repository không có ruleset. **Các lệnh dưới đây chưa được chạy**; tài liệu được đối chiếu lại với workflow ngày
03/10/2026 (W6-OPS) để chủ repo chạy được ngay.

## Điều kiện trước
- Tài khoản chạy lệnh có quyền **admin** trên `Babychandoi/Real-estate` và `gh auth status` hợp lệ.
- Repository riêng tư chỉ có branch protection/ruleset với gói GitHub Pro, Team hoặc Enterprise; với gói Free, API
  trả `403 Upgrade to GitHub Pro or make this repository public to enable this feature`.
- Phải có **ít nhất hai người** có quyền write: GitHub không cho tác giả PR tự duyệt PR của mình. Nếu hiện chỉ có một
  người, xem mục "Nếu chỉ có một maintainer" trước khi bật.

## Check nào bắt buộc, check nào chỉ tham khảo

Tên check là **id của job** vì các job không có khóa `name:` (đã kiểm tra `.github/workflows/ci.yml`). Nếu sau này thêm
`name:` cho job, tên check đổi theo giá trị đó và phải sửa ruleset tương ứng.

| Workflow | Job (= tên check) | Chạy khi | Bắt buộc? |
|---|---|---|---|
| `ci.yml` | `backend-tests` | mọi `pull_request`, push lên `main` | **Có** |
| `ci.yml` | `frontend-checks` | như trên | **Có** |
| `ci.yml` | `e2e` | như trên | **Có** |
| `ci.yml` | `security-scan` | như trên | **Có** |
| `performance.yml` | `mixed-load` | chỉ PR sửa các đường dẫn trong `on.pull_request.paths` + `workflow_dispatch` | Không — tham khảo |
| `operations-drills.yml` (đang thêm trong W6-OPS) | `recovery-drill`, `rollback-drill` | chỉ PR sửa các đường dẫn khai báo trong workflow + chạy tay | Không — tham khảo |

Vì sao không bắt buộc các workflow có lọc đường dẫn: khi PR không chạm các đường dẫn đó, workflow **không chạy và không
báo trạng thái nào**. Check bắt buộc khi đó nằm mãi ở "Expected — Waiting for status to be reported" và chặn merge mọi PR
không liên quan. Ruleset không có điều kiện "chỉ bắt buộc khi PR sửa đường dẫn X" cho status check, nên giữ chúng ở mức
tham khảo: người review xem kết quả của chúng trong tab Checks khi PR có chạm các file đó.

`ci.yml` không có `if:` ở mức job và không dùng `secrets.*`, nên cả bốn check đều báo trạng thái trên mọi PR, kể cả PR
của Dependabot (Dependabot không đọc được Actions secrets; CI hiện không cần).

Lấy `app.id` thật từ một lần chạy trên `main` (dùng làm `integration_id` bên dưới, thường là `15368` = GitHub Actions)
để một ứng dụng khác không báo trạng thái trùng tên thay cho CI:

```bash
OWNER=Babychandoi REPO=Real-estate
SHA=$(gh api "repos/$OWNER/$REPO/commits/main" --jq .sha)
gh api "repos/$OWNER/$REPO/commits/$SHA/check-runs" --jq '.check_runs[] | [.name, .app.id, .app.slug] | @tsv'
```
Kết quả mong đợi: bốn dòng `backend-tests`, `frontend-checks`, `e2e`, `security-scan` với cùng `app.id`. Nếu `app.id`
khác `15368`, thay số đó trong JSON dưới đây.

## Kiểu merge: không bật "lịch sử tuyến tính"

`main` đang dùng merge commit cho PR (ví dụ `13e41a2 merge: … (#21)` có hai commit cha; `811305a Merge pull request #18`).
Rule `required_linear_history` cấm merge commit trên nhánh, tức là bắt buộc squash hoặc rebase và chặn cách merge hiện
tại. Bản trước của tài liệu này có rule đó; **đã bỏ** vì không có lý do nào được ghi lại để đổi quy trình merge. Nếu sau
này chủ repo muốn lịch sử tuyến tính: bật lại rule **và** tắt "Allow merge commits" trong Settings → General cùng lúc.

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

- `strict_required_status_checks_policy: true`: PR phải cập nhật với `main` mới nhất trước khi merge (nút "Update branch").
- `require_last_push_approval: true`: người push commit cuối không tự duyệt được thay đổi của mình.
- `bypass_actors` để trống: kể cả admin cũng đi qua PR. Nếu cần lối thoát khẩn cấp, thêm vai trò admin với
  `"bypass_mode": "pull_request"` (vẫn phải qua PR) và ghi lý do mỗi lần dùng.
- Khi team `@security-reviewers`, `@database-reviewers`, `@platform-reviewers` trong `.github/CODEOWNERS` đã tồn tại
  thật, đổi `require_code_owner_review` thành `true`.

Kiểm tra cấu hình:
```bash
gh api "repos/$OWNER/$REPO/rulesets" --jq '.[] | [.id, .name, .enforcement] | @tsv'
gh api "repos/$OWNER/$REPO/rules/branches/main" --jq '.[].type'
# mong đợi: deletion, non_fast_forward, pull_request, required_status_checks (không có required_linear_history)
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
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
gh api "repos/$OWNER/$REPO/branches/main/protection" --jq '{checks: [.required_status_checks.checks[].context], reviews: .required_pull_request_reviews.required_approving_review_count, admins: .enforce_admins.enabled, linear: .required_linear_history.enabled}'
```

## Nếu chỉ có một maintainer
Với một người có quyền write, `required_approving_review_count: 1` làm mọi PR không merge được. Chọn một trong hai và
ghi lại lựa chọn:
- Đặt `required_approving_review_count` thành `0` (vẫn bắt buộc PR + bốn check xanh), nâng lên `1` khi có người thứ hai.
- Hoặc giữ `1` và thêm admin vào `bypass_actors` với `"bypass_mode": "pull_request"`; mỗi lần bypass ghi lý do trong PR.

## Kiểm tra sau khi bật
1. Tạo nhánh thử từ `main`, sửa một dòng không ảnh hưởng (ví dụ thêm khoảng trắng cuối file trong `docs/`), mở PR.
2. Trong lúc CI đang chạy: nút merge hiện "Merging is blocked" và danh sách "Required" liệt kê đúng bốn check
   `backend-tests`, `frontend-checks`, `e2e`, `security-scan`; `mixed-load`/`recovery-drill`/`rollback-drill` **không**
   có nhãn Required (và không xuất hiện nếu PR không chạm các đường dẫn của chúng).
3. Khi bốn check xanh nhưng chưa có review: vẫn bị chặn ("Review required"). Sau một approve của người khác: merge được
   bằng "Create a merge commit".
4. (Tùy chọn, chỉ sau khi bước 2–3 đã đúng) push thẳng một commit rỗng lên `main`:
   `git commit --allow-empty -m "test: branch protection" && git push origin HEAD:main` phải bị từ chối
   (`GH013: Repository rule violations` với ruleset, hoặc `protected branch hook declined` với cách 2). Sau đó
   `git reset --hard HEAD~1` cục bộ. Nếu push lọt: protection chưa có hiệu lực — dừng lại và kiểm tra cấu hình.
5. Đóng PR thử, xóa nhánh. Ghi ngày, người thực hiện và ảnh chụp màn hình bước 2–4 vào hồ sơ F01.9.

## Sau khi bật
- Dependabot vẫn mở PR bình thường; PR của nó cũng phải qua bốn check trên.
- Đổi tên job trong `ci.yml` (hoặc thêm `name:`) = phải sửa ruleset trong cùng đợt, nếu không mọi PR bị chặn chờ check
  tên cũ.
- Nếu sau này thực hiện phương án D của `docs/ops/GIT_HISTORY_REMEDIATION_PLAN.md` (tạo lại repository), phải chạy lại
  các lệnh này trên repository mới.
- Gỡ bỏ (khi thật sự cần): `gh api -X DELETE "repos/$OWNER/$REPO/rulesets/<id>"` hoặc
  `gh api -X DELETE "repos/$OWNER/$REPO/branches/main/protection"`.
