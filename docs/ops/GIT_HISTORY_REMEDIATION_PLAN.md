# Kế hoạch xử lý lịch sử Git chứa bản sao lưu production

Yêu cầu audit F21.3. **Đây là kế hoạch, chưa có bước nào được thực hiện.** Viết lại lịch sử, force-push, xóa hay tạo lại
repository là quyết định của chủ repository (`Babychandoi`) cùng người chịu trách nhiệm bảo mật/pháp lý; không agent
nào tự làm. Bối cảnh và phân loại dữ liệu: `docs/ops/BACKUP_CLASSIFICATION.md`.

## 1. Hiện trạng

- Commit `2d226a4` (26/09/2026) thêm `backups/20260926-005833/` (5 archive qua Git LFS + README) và một dòng
  `.gitattributes`. Commit đã có trên `origin/main` và `origin/feat/macos-uat-compare-map-profiles`.
- Các commit sau nó trên `main` (`2df362f`, `b98c6a6`, `409b654`, `b8ebb15`, `831a010`) và toàn bộ nhánh audit
  (`audit-2026-09-27`, `audit/*`) đều kế thừa commit này; viết lại lịch sử sẽ đổi SHA của tất cả.
- Báo cáo audit `Real-estate_Audit_2026-09-27.md` dẫn chứng bằng link khóa theo SHA `831a010...`.
- Đối tượng LFS nằm trong kho LFS của GitHub; GitHub chỉ xóa chúng khi repository bị xóa (tài liệu GitHub "Removing
  files from Git Large File Storage"). Ai có quyền đọc và biết OID (có trong file con trỏ của mọi clone) đều tải được,
  kể cả khi không còn commit nào tham chiếu.

## 2. Các phương án

| Phương án | Làm gì | Loại bỏ được | Không loại bỏ được | Hệ quả |
|---|---|---|---|---|
| **A. Giữ nguyên** | Không làm gì; chỉ chặn backup mới (đã làm: `.gitignore`, công cụ backup mã hóa) | — | Mọi thứ | Chỉ chấp nhận được nếu chủ dự án xác nhận dữ liệu không phải người dùng thật; theo đợt audit thì không phải trường hợp này |
| **B. Xóa khỏi HEAD** | `git rm -r backups/20260926-005833` + commit thường | Bản checkout mới không còn file | Lịch sử, đối tượng LFS, clone, fork | Không đổi SHA; gần như chỉ mang tính hình thức |
| **C. Viết lại lịch sử** | `git filter-repo` bỏ `backups/`, force-push mọi nhánh/tag, mọi người clone lại | Commit trong repository trên GitHub | **Đối tượng LFS trên GitHub** (vẫn tải được theo OID), clone/fork cũ | Đổi SHA của mọi commit từ `2d226a4`; PR mở bị hỏng; link khóa SHA bị gãy |
| **D. Viết lại + tạo lại repository** (khuyến nghị nếu dữ liệu là thật) | Như C, rồi xóa (hoặc cách ly rồi xóa) repository cũ và tạo repository mới từ lịch sử đã làm sạch | Commit **và** đối tượng LFS trên GitHub, fork của repository riêng tư cũ | Clone/bản sao đã nằm trên máy người khác (chỉ xử lý được bằng yêu cầu xóa và cam kết) | Mất issue, PR, lịch sử Actions, star, cấu hình (branch protection, secrets, webhooks, environments) — phải cấu hình lại |

Lưu ý khi chọn C/D: nên làm **sau khi** các nhánh `audit/*` đã gộp vào `audit-2026-09-27` và `main`, để chỉ phải
chuyển một lịch sử duy nhất; trong lúc chờ, thu hẹp quyền đọc repository (BACKUP_CLASSIFICATION.md mục 3).

## 3. Quy trình cho phương án D (C là các bước 1–4 và 6–8)

### Bước 0 — Chuẩn bị (người thực hiện: chủ repo)
- Thông báo đóng băng: không merge, không push trong lúc thực hiện.
- Kiểm kê cấu hình để làm lại: collaborator/team, deploy key, webhook, GitHub Apps, Actions secrets/variables,
  environments, Dependabot, branch protection/rulesets, repository settings.
- Ghi lại danh sách nhánh và tag: `git ls-remote --heads --tags origin > refs-before.txt`.

### Bước 1 — Bản mirror cách ly (để đối chiếu hoặc rollback trong thời hạn đã định)
```bash
git clone --mirror https://github.com/Babychandoi/Real-estate.git real-estate-before-rewrite.git
git -C real-estate-before-rewrite.git lfs fetch --all          # chứa cả archive: xử lý như dữ liệu Mật – cá nhân
tar -C . -cf - real-estate-before-rewrite.git | zstd | age -r <owner-age-public-key> > real-estate-before-rewrite.tar.zst.age
rm -rf real-estate-before-rewrite.git                            # chỉ giữ bản mã hóa, ngoài máy dev, có ngày hủy
```

### Bước 2 — Viết lại trên một bản mirror mới
```bash
git clone --mirror https://github.com/Babychandoi/Real-estate.git real-estate-rewrite.git
cd real-estate-rewrite.git
git for-each-ref --format='%(refname)' refs/pull | xargs -r -n 1 git update-ref -d   # ref PR của GitHub không push lại được
git filter-repo --path backups/ --invert-paths
```
`git filter-repo` (https://github.com/newren/git-filter-repo) ghi bảng ánh xạ SHA cũ → mới vào `filter-repo/commit-map`;
giữ file này để cập nhật tài liệu.

### Bước 3 — Kiểm tra
```bash
git log --all --oneline -- backups/ | wc -l                      # phải là 0
git rev-list --objects --all | grep -c ' backups/' || true       # phải là 0
git lfs ls-files --all 2>/dev/null | grep -c backups || true     # phải là 0
git grep -n 'filter=lfs' $(git rev-list --all -n 1) -- .gitattributes   # dòng LFS cho backups: xóa bằng một commit thường sau khi push
```

### Bước 4 — Đẩy lên
- **C:** `git push --force --all origin && git push --force --tags origin`, rồi xóa trên GitHub mọi nhánh cũ không còn
  trong `refs-before.txt` sau viết lại.
- **D:** đổi tên repository cũ thành `Real-estate-quarantine` và giới hạn quyền cho owner; tạo repository riêng tư mới
  `Babychandoi/Real-estate`; `git remote set-url origin https://github.com/Babychandoi/Real-estate.git`;
  `git push --all origin && git push --tags origin`.

### Bước 5 — (D) Xóa repository cũ
Sau khi repository mới chạy ổn và bản mirror mã hóa đã lưu: xóa `Real-estate-quarantine` (Settings → Danger Zone).
Việc này xóa đối tượng LFS và fork của repository riêng tư cũ. Không thể hoàn tác ngoài việc khôi phục từ bản mirror.

### Bước 6 — Cấu hình lại và chặn tái diễn
- Làm lại cấu hình theo kiểm kê ở bước 0; branch protection theo `docs/ops/BRANCH_PROTECTION.md`.
- Nếu gói GitHub hỗ trợ push ruleset: chặn đường dẫn `backups/**` và file lớn (ví dụ > 50 MB) ở mọi nhánh.
- Giữ `.gitignore` (`backups/`, `*.dump.age`) và quy tắc "backup chỉ ở `BACKUP_DIR` ngoài repository".

### Bước 7 — Mọi cộng tác viên
- Xóa mọi clone và worktree cũ (kể cả `/Users/connecty/Real-estate` và `.claude/worktrees/*` trên máy đang chạy
  production — stack đang chạy không phụ thuộc thư mục `.git`, nhưng lần deploy kế tiếp phải từ clone mới).
- Clone lại; **không bao giờ push nhánh cũ** (sẽ đưa đối tượng trở lại). Xóa bản sao cũ trong Time Machine/iCloud nếu có.

### Bước 8 — Cập nhật tài liệu
Link khóa SHA trong `Real-estate_Audit_2026-09-27.md` và các tài liệu khác: thay bằng SHA mới từ
`filter-repo/commit-map`, hoặc ghi chú rằng link trỏ tới lịch sử trước khi làm sạch.

### Rollback
Trong thời hạn giữ bản mirror: giải mã và push lại lịch sử cũ. Làm vậy sẽ đưa dữ liệu nhạy cảm trở lại GitHub, chỉ dùng
khi lịch sử mới hỏng nghiêm trọng và không sửa được.

## 4. Danh sách xoay vòng bí mật

Độc lập với phương án chọn, vì dữ liệu đã có thể bị sao chép.

| # | Bí mật / dữ liệu | Vì sao liên quan | Việc cần làm | Kiểm tra |
|---|---|---|---|---|
| 1 | Mật khẩu PostgreSQL (`POSTGRES_PASSWORD`) | Verifier SCRAM trong `pg_authid` nằm trong volume | `ALTER ROLE <user> PASSWORD '<mới>'`, cập nhật `.env`, recreate backend | Mật khẩu cũ bị từ chối |
| 2 | Phiên đăng nhập đang mở | Chỉ có băm của token 256 bit (không đảo được), nhưng thu hồi là rẻ | `UPDATE auth_sessions SET revoked_at = now() WHERE revoked_at IS NULL;` | Token cũ trả 401 |
| 3 | Mật khẩu người dùng | Băm bcrypt cost 12 có thể bị dò ngoại tuyến | Bắt buộc đặt lại cho ADMIN/MODERATOR ngay; với người dùng: quyết định sản phẩm/pháp lý về việc thông báo và yêu cầu đổi mật khẩu | Staff đăng nhập bằng mật khẩu mới |
| 4 | MinIO root (`MINIO_ROOT_USER/PASSWORD`) và mọi IAM user | Cấu hình IAM trong `.minio.sys` nằm trong volume | Đổi root password, tạo lại access key của IAM user; recreate MinIO và backend | Key cũ bị từ chối |
| 5 | `PII_ENCRYPTION_KEY`, `PII_INDEX_KEY` | Không nằm trong volume, nhưng bản mã số điện thoại thì có: nếu khóa từng lộ (ví dụ `.env` trên máy dev), mọi số điện thoại lộ theo | Bảo vệ khóa; nếu có nghi ngờ: xoay khóa **kèm** mã hóa lại dữ liệu — hiện mã nguồn chỉ có một phiên bản khóa (`v1:`), cần một task kỹ thuật hỗ trợ nhiều phiên bản khóa trước khi xoay | Đọc được số điện thoại sau khi xoay |
| 6 | Redis (`REDIS_PASSWORD`) | Không nằm trong AOF (mật khẩu truyền qua dòng lệnh) | Không bắt buộc; xoay nếu muốn làm sạch hoàn toàn | — |
| 7 | SMTP app password, credential Cloudflare Tunnel, `OUTBOX_SIGNING_KEY`, `DEMO_ACCOUNT_PASSWORD` | Không nằm trong volume; chỉ liên quan nếu `.env` hoặc `~/.cloudflared` trên máy dev bị lộ | Quét toàn bộ lịch sử Git tìm bí mật (lệnh dưới); xoay ngay nếu thấy | Không còn phát hiện |
| 8 | Token xác minh email, đặt lại mật khẩu, quyền xem KYC | Chỉ lưu băm; hết hạn sau 24 giờ / 30 phút / 10 phút | Không cần | — |
| 9 | Khóa age của bản sao lưu mới | Không liên quan tới archive cũ | — | — |

Quét bí mật trên toàn bộ lịch sử (chỉ đọc, không gửi dữ liệu ra ngoài):
```bash
# chạy trong một clone thường (không phải worktree) của repository
docker run --rm -v "$PWD:/repo:ro" zricethezav/gitleaks:v8.28.0 git /repo --log-opts="--all" --redact --no-banner
```

## 5. Quyết định cần ghi lại

| Câu hỏi | Người quyết định | Ghi vào |
|---|---|---|
| Dữ liệu trong archive là của người dùng thật? | Chủ dự án | Tài liệu này (phụ lục) |
| Chọn A/B/C/D, thời điểm thực hiện | Chủ repository | Tài liệu này + thông báo cho cộng tác viên |
| Có phải thông báo cho người dùng/cơ quan quản lý không | Người có chuyên môn pháp lý | Hồ sơ sự cố |
| Thời hạn giữ bản mirror mã hóa và ngày hủy | Chủ dự án | Tài liệu này |
