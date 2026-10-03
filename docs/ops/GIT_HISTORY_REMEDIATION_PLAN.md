# Kế hoạch xử lý lịch sử Git chứa bản sao lưu production

Yêu cầu audit F21.3. **Đây là kế hoạch, chưa có bước nào được thực hiện.** Viết lại lịch sử, force-push, xóa hay tạo lại
repository là quyết định của chủ repository (`Babychandoi`) cùng người chịu trách nhiệm bảo mật/pháp lý; không agent
nào tự làm. Bối cảnh và phân loại dữ liệu: `docs/ops/BACKUP_CLASSIFICATION.md`.

## 1. Hiện trạng

> **Repository là CÔNG KHAI** (`visibility: public`, tạo 11/09/2026; 0 fork — kiểm tra 03/10/2026 bằng
> `gh api repos/Babychandoi/Real-estate --jq '.visibility, .forks_count'`). `backups/20260926-005833/*.tar.gz` (ảnh
> KYC/CCCD, email, băm bcrypt — `BACKUP_CLASSIFICATION.md`) nằm **trong cây hiện tại của `main`** và trong kho LFS, nên
> **ai trên Internet cũng tải được từ 26/09/2026** (commit đầu tiên chứa dữ liệu: `2d226a4`), không cần tài khoản. Chủ dự
> án quyết định tạm giữ repository công khai (03/10/2026); vì vậy bước 0 và quyết định pháp lý ở mục 5 là **khẩn cấp**.

- Commit `2d226a4` (26/09/2026) thêm `backups/20260926-005833/` (5 archive qua Git LFS + README) và một dòng
  `.gitattributes`. Commit đã có trên `origin/main` và `origin/feat/macos-uat-compare-map-profiles`.
- Các commit sau nó trên `main` (`2df362f`, `b98c6a6`, `409b654`, `b8ebb15`, `831a010`) và toàn bộ nhánh audit
  (`audit-2026-09-27`, `audit/*`) đều kế thừa commit này; viết lại lịch sử sẽ đổi SHA của tất cả.
- Cập nhật 03/10/2026 (clone cục bộ sau `git fetch`): `main` có 255 commit sau `2d226a4` (gồm cả các merge #16–#21);
  20/25 nhánh remote chứa commit này (`git branch -r --contains 2d226a4`). Số này tăng theo mỗi commit mới.
- Báo cáo audit `Real-estate_Audit_2026-09-27.md` dẫn chứng bằng link khóa theo SHA `831a010...`.
- Đối tượng LFS nằm trong kho LFS của GitHub; GitHub chỉ xóa chúng khi repository bị xóa (tài liệu GitHub "Removing
  files from Git Large File Storage"). Ai có quyền đọc và biết OID (có trong file con trỏ của mọi clone) đều tải được,
  kể cả khi không còn commit nào tham chiếu.

### Kiểm kê những gì đã lộ

| Nhóm | Đường dẫn / loại | Bí mật hoặc dữ liệu bên trong | Nguồn |
|---|---|---|---|
| Archive LFS | `backups/20260926-005833/postgres-data.tar.gz` | Dữ liệu cá nhân (email, họ tên, lead, hồ sơ KYC, bản mã số điện thoại và số CCCD), băm bcrypt mật khẩu, verifier SCRAM của role PostgreSQL, băm token phiên | `BACKUP_CLASSIFICATION.md` §1 |
| Archive LFS | `backups/20260926-005833/minio-data.tar.gz` | Ảnh KYC không mã hóa, ảnh tin/avatar, cấu hình IAM MinIO (`.minio.sys`) | như trên |
| Archive LFS | `redis-data.tar.gz`, `elasticsearch-data.tar.gz`, `clamav-data.tar.gz` | Bộ đếm rate limit theo IP; chỉ mục tin công khai; chữ ký virus công khai | như trên |
| File thường | `backups/20260926-005833/README.md`, dòng LFS trong `.gitattributes` | Mô tả, kích thước, checksum (Nội bộ) | như trên |
| Không thấy trong lịch sử | `.env*` (ngoài 3 file `*.example` chỉ chứa giá trị `replace…`), `infra/cloudflared/credentials.json`, `*.pem`, `*.key` | — | `git log --all --diff-filter=A --name-only` trên clone cục bộ ngày 03/10/2026. **Chưa phải quét bí mật**: kết luận cuối lấy từ bước 1b trên bản mirror đầy đủ |
| Không nằm trong archive | `PII_ENCRYPTION_KEY`, `PII_INDEX_KEY`, mọi secret trong `.env`, private key age | Chỉ lộ nếu máy dev hoặc `.env` lộ | `docs/operations/PRODUCTION_ENV.md` |

Bí mật TOTP của staff (`user_mfa`, migration V088, thêm 28/09/2026) có sau snapshot 26/09 nên không nằm trong archive;
token phiên/xác minh/quyền xem KYC chỉ lưu dạng băm.

## 2. Các phương án

| Phương án | Làm gì | Loại bỏ được | Không loại bỏ được | Hệ quả |
|---|---|---|---|---|
| **A. Giữ nguyên** | Không làm gì; chỉ chặn backup mới (đã làm: `.gitignore`, công cụ backup mã hóa) | — | Mọi thứ | Chỉ chấp nhận được nếu chủ dự án xác nhận dữ liệu không phải người dùng thật; theo đợt audit thì không phải trường hợp này |
| **B. Xóa khỏi HEAD** | `git rm -r backups/20260926-005833` + commit thường | Bản checkout/tải ZIP mới không còn file | Lịch sử, đối tượng LFS (vẫn tải công khai theo SHA cũ), clone | Không đổi SHA; với repository công khai đây là mức tối thiểu phải làm ngay (bước 0b), không phải giải pháp |
| **C. Viết lại lịch sử** | `git filter-repo` bỏ `backups/`, force-push mọi nhánh/tag, mọi người clone lại | Commit trong repository trên GitHub | **Đối tượng LFS trên GitHub** (vẫn tải được theo OID), clone/fork cũ | Đổi SHA của mọi commit từ `2d226a4`; PR mở bị hỏng; link khóa SHA bị gãy |
| **D. Viết lại + tạo lại repository** (khuyến nghị nếu dữ liệu là thật) | Như C, rồi xóa (hoặc cách ly rồi xóa) repository cũ và tạo repository mới từ lịch sử đã làm sạch | Commit **và** đối tượng LFS trên GitHub (fork của repository công khai **không** tự mất khi xóa repository gốc — hiện có 0 fork) | Clone/bản sao đã nằm trên máy người khác (chỉ xử lý được bằng yêu cầu xóa và cam kết) | Mất issue, PR, lịch sử Actions, star, cấu hình (branch protection, secrets, webhooks, environments) — phải cấu hình lại |

Lưu ý khi chọn C/D: không chờ gộp các nhánh — với repository công khai, mỗi ngày chờ là thêm một ngày dữ liệu tải được
công khai. Bước 0 làm trước, độc lập với C/D.

### Bước 0 — KHẨN CẤP: chặn tải công khai (chủ repository chọn một trong hai)

| Lựa chọn | Làm gì | Hiệu lực | Đánh đổi |
|---|---|---|---|
| **0a. Chuyển sang riêng tư** | Settings → General → Danger Zone → Change visibility → Private | Ngay: người ngoài không còn tải được commit lẫn đối tượng LFS | Gói Free cho repository riêng tư: phút GitHub Actions có giới hạn/tháng (CI hiện chạy e2e và các drill dài hàng chục phút mỗi PR, sẽ hết nhanh); **branch protection/rulesets không dùng được** (`docs/ops/BRANCH_PROTECTION.md`); bản đã bị tải trước đó không thu hồi được |
| **0b. Giữ công khai, gỡ dữ liệu** | (1) phương án B: bỏ `backups/20260926-005833` và dòng LFS trong `.gitattributes` khỏi cây, merge vào `main` ngay; (2) phương án C (bước 1–4); (3) **yêu cầu GitHub Support xóa các đối tượng LFS** cùng cache/ref cũ (đối tượng LFS chỉ mất khi Support xóa hoặc khi xóa repository) | (1) chỉ chặn bản checkout mới; chỉ sau (2)+(3) dữ liệu mới thật sự hết trên GitHub | Đổi SHA mọi commit từ `2d226a4`; phụ thuộc thời gian xử lý của GitHub Support; giữ Actions không giới hạn và rulesets của repository công khai |

Chủ dự án hiện chọn giữ công khai ⇒ 0b là đường mặc định: bước (1) càng sớm càng tốt (một PR thường), (2)+(3) theo quy
trình dưới đây. Trong mọi trường hợp: xoay bí mật theo mục 4 **trước** khi viết lại lịch sử, và quyết định pháp lý ở mục 5
**ngay**.

## 3. Quy trình cho phương án D (C là các bước 1–4 và 6–8)

### Bước 0 — Chuẩn bị (người thực hiện: chủ repo)
- Thông báo đóng băng: không merge, không push trong lúc thực hiện.
- Kiểm kê cấu hình để làm lại: collaborator/team, deploy key, webhook, GitHub Apps, Actions secrets/variables,
  environments, Dependabot, branch protection/rulesets, repository settings.
- Ghi lại danh sách nhánh và tag: `git ls-remote --heads --tags origin > refs-before.txt`.

### Thứ tự bắt buộc: xoay vòng trước, viết lại sau
Viết lại lịch sử không thu hồi được bản đã bị sao chép, còn bí mật cũ vẫn dùng được cho tới khi bị xoay. Vì vậy:
1. Xoay vòng các mục "Ngay" ở mục 4 và xác nhận giá trị cũ bị từ chối — **trước** khi đụng tới lịch sử.
2. Bước 0 (repository công khai: quyền đọc là của mọi người); thu hồi deploy key/PAT/GitHub App không còn dùng.
3. Quét bí mật (bước 1b); xoay thêm bất cứ thứ gì được phát hiện.
4. Rồi mới làm bước 1–8 (nếu chọn C/D).

### Bước 1 — Bản mirror cách ly (để đối chiếu hoặc rollback trong thời hạn đã định)
```bash
git clone --mirror https://github.com/Babychandoi/Real-estate.git real-estate-before-rewrite.git
git -C real-estate-before-rewrite.git lfs fetch --all          # chứa cả archive: xử lý như dữ liệu Mật – cá nhân
tar -C . -cf - real-estate-before-rewrite.git | zstd | age -r <owner-age-public-key> > real-estate-before-rewrite.tar.zst.age
rm -rf real-estate-before-rewrite.git                            # chỉ giữ bản mã hóa, ngoài máy dev, có ngày hủy
```

### Bước 1b — Quét bí mật trên toàn bộ lịch sử (chỉ đọc, chạy cục bộ, trước khi viết lại)
Chạy trên một bản `git clone --mirror` mới (có cả `refs/pull/*`), không tải LFS. Kết quả quyết định có phải xoay thêm
bí mật ngoài mục 4 không; lưu báo cáo (đã `--redact`) cùng hồ sơ sự cố.
```bash
git clone --mirror https://github.com/Babychandoi/Real-estate.git scan.git
docker run --rm -v "$PWD/scan.git:/repo:ro" zricethezav/gitleaks:v8.28.0 git /repo --log-opts="--all" --redact --no-banner
docker run --rm -v "$PWD/scan.git:/repo:ro" trufflesecurity/trufflehog:3.97.9 git file:///repo --no-verification --no-update
```
`--no-verification`: trufflehog không gọi API của nhà cung cấp để thử khóa (không gửi gì ra ngoài).

### Bước 2 — Viết lại trên một bản mirror mới
```bash
git clone --mirror https://github.com/Babychandoi/Real-estate.git real-estate-rewrite.git
cd real-estate-rewrite.git
git for-each-ref --format='%(refname)' refs/pull | xargs -r -n 1 git update-ref -d   # ref PR của GitHub không push lại được
git filter-repo --path backups/ --invert-paths
```
`git filter-repo` (https://github.com/newren/git-filter-repo) ghi bảng ánh xạ SHA cũ → mới vào `filter-repo/commit-map`;
giữ file này để cập nhật tài liệu. Cài: `brew install git-filter-repo` hoặc `pip install git-filter-repo`. filter-repo
**tự gỡ remote `origin`** sau khi chạy để tránh push nhầm; bước 4 thêm lại có chủ đích.

### Bước 3 — Kiểm tra
```bash
git log --all --oneline -- backups/ | wc -l                      # phải là 0
git rev-list --objects --all | grep -c ' backups/' || true       # phải là 0
git lfs ls-files --all 2>/dev/null | grep -c backups || true     # phải là 0
git grep -n 'filter=lfs' $(git rev-list --all -n 1) -- .gitattributes   # dòng LFS cho backups: xóa bằng một commit thường sau khi push
```

### Bước 4 — Đẩy lên
- **C:** `git remote add origin https://github.com/Babychandoi/Real-estate.git`, rồi
  `git push --force --all origin && git push --force --tags origin` (không dùng `--mirror`: GitHub từ chối cập nhật
  `refs/pull/*`); xóa trên GitHub mọi nhánh cũ không còn trong `refs-before.txt` sau viết lại, kể cả `dependabot/*`
  (Dependabot tự tạo lại).
- **C — bắt buộc thêm:** gửi yêu cầu tới GitHub Support (https://support.github.com, chủ đề xóa dữ liệu nhạy cảm) với tên
  repository, SHA `2d226a4` và danh sách PR có commit cũ, đề nghị xóa cached views, ref `refs/pull/*` cũ và chạy garbage
  collection. Thiếu bước này, commit cũ vẫn mở được qua PR và URL theo SHA. Đối tượng LFS vẫn còn (chỉ mất ở phương án D).
- **D:** đổi tên repository cũ thành `Real-estate-quarantine` và **chuyển nó sang riêng tư** ngay (đang công khai); tạo
  repository mới `Babychandoi/Real-estate` (công khai hay riêng tư theo quyết định ở bước 0); `git remote add origin https://github.com/Babychandoi/Real-estate.git`;
  `git push --all origin && git push --tags origin`.

### Bước 4b — Kiểm chứng sau khi đẩy
```bash
git clone https://github.com/Babychandoi/Real-estate.git verify && cd verify
git log --all --oneline -- backups/ | wc -l                                   # 0
docker run --rm -v "$PWD:/repo:ro" zricethezav/gitleaks:v8.28.0 git /repo --log-opts="--all" --redact --no-banner
gh api repos/Babychandoi/Real-estate/commits/2d226a4 --jq .sha                 # D: lỗi 404/422; C: chỉ 404 sau khi Support xử lý
```
Ghi kết quả (số phát hiện, ngày, người chạy) vào phụ lục tài liệu này.

### Bước 5 — (D) Xóa repository cũ
Sau khi repository mới chạy ổn và bản mirror mã hóa đã lưu: xóa `Real-estate-quarantine` (Settings → Danger Zone).
Việc này xóa đối tượng LFS của repository cũ; fork của một repository công khai **không** bị xóa theo (hiện 0 fork —
kiểm tra lại ngay trước bước này). Không thể hoàn tác ngoài việc khôi phục từ bản mirror.

### Bước 6 — Cấu hình lại và chặn tái diễn
- Làm lại cấu hình theo kiểm kê ở bước 0; branch protection theo `docs/ops/BRANCH_PROTECTION.md`.
- Nếu gói GitHub hỗ trợ push ruleset: chặn đường dẫn `backups/**` và file lớn (ví dụ > 50 MB) ở mọi nhánh.
- Giữ `.gitignore` (`backups/`, `*.dump.age`) và quy tắc "backup chỉ ở `BACKUP_DIR` ngoài repository".

### Bước 7 — Mọi cộng tác viên
- Xóa mọi clone và worktree cũ (kể cả `/Users/connecty/Real-estate` và `.claude/worktrees/*` trên máy đang chạy
  production — stack đang chạy không phụ thuộc thư mục `.git`, nhưng lần deploy kế tiếp phải từ clone mới).
- Clone lại; **không bao giờ push nhánh cũ** (sẽ đưa đối tượng trở lại). Xóa bản sao cũ trong Time Machine/iCloud nếu có.
- Fork: liệt kê bằng `gh api repos/Babychandoi/Real-estate/forks --jq '.[].full_name'` (0 ngày 03/10/2026) và yêu cầu
  chủ fork xóa; với repository công khai, fork không tự mất ở cả C lẫn D.
- PR đang mở: đóng, rồi mở lại từ nhánh đã rebase lên lịch sử mới (PR cũ trỏ vào commit cũ).

### Bước 8 — Cập nhật tài liệu
Link khóa SHA trong `Real-estate_Audit_2026-09-27.md` và các tài liệu khác: thay bằng SHA mới từ
`filter-repo/commit-map`, hoặc ghi chú rằng link trỏ tới lịch sử trước khi làm sạch.

### Rollback
Trong thời hạn giữ bản mirror: giải mã và push lại lịch sử cũ. Làm vậy sẽ đưa dữ liệu nhạy cảm trở lại GitHub, chỉ dùng
khi lịch sử mới hỏng nghiêm trọng và không sửa được.
- D, trước bước 5: chỉ cần đổi tên `Real-estate-quarantine` về tên cũ (sau khi đổi tên/xóa repository mới) — repository
  cũ còn nguyên issue, PR, cấu hình. Vì vậy chỉ làm bước 5 khi repository mới đã chạy ổn ít nhất vài ngày.
- Xoay vòng bí mật **không bao giờ rollback** về giá trị cũ, kể cả khi rollback lịch sử.
- Mốc dừng: nếu bước 3 hoặc 4b còn thấy `backups/` hay phát hiện bí mật mới, không xóa gì thêm; sửa trên bản mirror rồi
  đẩy lại.

## 4. Danh sách xoay vòng bí mật

Độc lập với phương án chọn, vì dữ liệu đã có thể bị sao chép. Danh sách bao phủ mọi secret trong
`docs/operations/PRODUCTION_ENV.md`, `docker-compose.yml` và các overlay `infra/compose.*.yaml`. Cột **Khi nào**:
**Ngay** = có trong archive đã lộ; **Khi tách máy** = nằm trong `.env`/máy dev, xoay khi chuyển production khỏi máy dev
(`docs/operations/PRODUCTION_SEPARATION_PLAN.md`) hoặc ngay khi bước 1b phát hiện; **Không cần** = chỉ lưu băm/hết hạn.
Sau mỗi thay đổi secret runtime: `docker compose -p bds-production -f docker-compose.yml -f infra/compose.apple-silicon.yaml up -d --no-deps --force-recreate <dịch vụ>` (chỉ tạo lại đúng dịch vụ đó; đổi mật khẩu PostgreSQL/Redis/MinIO thì tạo lại cả dịch vụ đó lẫn backend, theo `docs/operations/PRODUCTION_ENV.md`).

| # | Bí mật / dữ liệu | Khi nào | Vì sao liên quan | Việc cần làm | Kiểm tra |
|---|---|---|---|---|---|
| 1 | `POSTGRES_PASSWORD` = `SPRING_DATASOURCE_PASSWORD`; cả `BACKUP_PGPASSWORD`, `PG_EXPORTER_PASSWORD` nếu đang dùng chung role ứng dụng (mặc định) | Ngay | Verifier SCRAM trong `pg_authid` nằm trong volume | `ALTER ROLE <user> PASSWORD '<mới>'` **trước**, rồi sửa `.env` và recreate backend/backup/exporter; nên tách role riêng cho backup (`pg_read_all_data`) và exporter (`pg_monitor`) | Mật khẩu cũ bị từ chối; `/backend-health` xanh |
| 2 | Phiên đăng nhập đang mở | Ngay | Chỉ có băm token 256 bit (không đảo được), nhưng thu hồi là rẻ | `UPDATE auth_sessions SET revoked_at = now() WHERE revoked_at IS NULL;` | Token cũ trả 401 |
| 3 | Mật khẩu người dùng, kể cả tài khoản demo (`DEMO_ACCOUNT_PASSWORD`) nếu tồn tại trong DB production | Ngay | Băm bcrypt cost 12 có thể bị dò ngoại tuyến | Bắt buộc đặt lại cho ADMIN/MODERATOR và tài khoản demo; với người dùng: quyết định sản phẩm/pháp lý về thông báo và yêu cầu đổi mật khẩu; đổi giá trị `DEMO_ACCOUNT_PASSWORD` | Đăng nhập bằng mật khẩu cũ thất bại |
| 4 | `MINIO_ROOT_USER/PASSWORD` và mọi IAM user (gồm `BACKUP_MINIO_ACCESS_KEY/SECRET_KEY`) | Ngay | Cấu hình IAM trong `.minio.sys` nằm trong volume | Đổi root password, tạo lại access key của IAM user; recreate MinIO, backend, backup cùng lúc | Key cũ bị từ chối; upload ảnh thử thành công |
| 5 | `PII_ENCRYPTION_KEY` | Khi tách máy / nếu nghi lộ | Không nằm trong volume, nhưng bản mã thì có: số điện thoại, số CCCD (`id_number_encrypted`), bí mật TOTP (`user_mfa.secret_sealed`) | **Không thay trực tiếp** (mất khả năng giải mã). Mã hiện chỉ có một phiên bản khóa (`v1:` trong `PiiProtectionService`): cần task kỹ thuật hỗ trợ khóa nhiều phiên bản + job mã hóa lại trước khi xoay; hoặc staff đăng ký lại MFA | Đọc được số điện thoại/CCCD và đăng nhập MFA sau khi xoay |
| 6 | `PII_INDEX_KEY` | Khi tách máy / nếu nghi lộ | Blind index HMAC (`phone_lookup_hash`, `id_number_lookup_hash`) trong volume; có khóa thì dò được số điện thoại | Xoay **kèm** dựng lại blind index (cùng task kỹ thuật ở #5) | Tìm theo số điện thoại vẫn đúng |
| 7 | `REDIS_PASSWORD` = `SPRING_DATA_REDIS_PASSWORD` | Khi tách máy | Không nằm trong AOF (truyền qua dòng lệnh) | Đổi hai biến cùng lúc, recreate Redis + backend | `redis-cli -a <cũ> ping` bị từ chối |
| 8 | `SEARCH_CURSOR_SECRET` | Khi tách máy | HMAC cursor phân trang tìm kiếm | Đổi giá trị (≥ 32 ký tự, giống nhau mọi instance); cursor đang mở trở nên không hợp lệ, người dùng tải lại từ trang đầu | "Xem thêm" chạy trên phiên tìm kiếm mới |
| 9 | `MEDIA_SIGNING_SECRET` | Khi tách máy | HMAC URL ảnh ký (tin nháp/ẩn) | Xoay êm: chép giá trị hiện tại sang `MEDIA_PREVIOUS_SIGNING_SECRET`, đặt giá trị mới, recreate backend; sau ít nhất một `MEDIA_SIGNED_URL_TTL` (mặc định 15 phút) xóa `MEDIA_PREVIOUS_SIGNING_SECRET` và recreate lại | URL ký trước khi xoay vẫn mở được trong TTL, bị từ chối sau khi xóa giá trị cũ |
| 10 | `RATE_LIMIT_KEY_PEPPER` | Khi tách máy | Bí mật trộn vào khóa rate limit | Đổi giá trị; bộ đếm đang chạy về 0 (chấp nhận được) | Dashboard Grafana "BDS — Rate limit" tiếp tục có số liệu |
| 11 | `OUTBOX_SIGNING_KEY` | Khi tách máy | Ký webhook outbox | Hiện `OUTBOX_ENABLED=false`: đổi bất cứ lúc nào; khi đã bật, phối hợp bên nhận trước | — |
| 12 | `SPRING_MAIL_PASSWORD` (Gmail App Password) | Khi tách máy | Gửi mail thay mặt `APP_MAIL_FROM` | Thu hồi App Password cũ trong tài khoản Google, tạo mới | Mail xác minh thử tới nơi |
| 13 | Credential Cloudflare Tunnel (`infra/cloudflared/credentials.json`, trước đây `~/.cloudflared`) | Khi tách máy | Ai có file chạy được connector thay origin | Tạo tunnel/connector mới trên máy production mới, chuyển route DNS, rồi `cloudflared tunnel delete <tunnel-cũ>` (thu hồi credential cũ) | `cloudflared tunnel list` chỉ còn tunnel mới; site vẫn trả 200 |
| 14 | Private key age của bản sao lưu | Nếu từng nằm trên máy dev/production | Mở được mọi bản `.age` đã mã hóa cho nó | Tạo cặp khóa mới, đổi `BACKUP_AGE_RECIPIENTS`; giữ key cũ ngoại tuyến tới khi các set cũ hết hạn lưu (14 ngày), rồi hủy | Bản sao lưu mới giải mã bằng key mới (`scripts/restore-drill.sh`) |
| 15 | `GRAFANA_ADMIN_PASSWORD` | Khi tách máy | Quyền admin Grafana | Biến môi trường chỉ áp dụng lần khởi tạo đầu: đổi bằng `docker compose … exec grafana grafana cli admin reset-admin-password '<mới>'` rồi cập nhật `.env` | Đăng nhập bằng mật khẩu cũ thất bại |
| 16 | Quyền truy cập GitHub: collaborator, deploy key, PAT, GitHub App | Ngay | Repository công khai: ai cũng tải được LFS; quyền ghi vẫn phải rà soát | Rà soát theo `BACKUP_CLASSIFICATION.md` §3; thu hồi thứ không dùng. CI hiện không dùng Actions secrets | Danh sách sau rà soát lưu vào hồ sơ |
| 17 | Token xác minh email, đặt lại mật khẩu, quyền xem KYC | Không cần | Chỉ lưu băm; hết hạn sau 24 giờ / 30 phút / 10 phút | — | — |

Lệnh quét bí mật: bước 1b (trước khi viết lại) và bước 4b (sau khi đẩy).

## 5. Quyết định cần ghi lại

| Câu hỏi | Người quyết định | Ghi vào |
|---|---|---|
| Dữ liệu trong archive là của người dùng thật? | Chủ dự án | Tài liệu này (phụ lục) |
| Chọn A/B/C/D, thời điểm thực hiện | Chủ repository | Tài liệu này + thông báo cho cộng tác viên |
| **KHẨN CẤP** — Có phải thông báo cho người dùng/cơ quan quản lý không, trong thời hạn nào (dữ liệu cá nhân nhạy cảm, gồm ảnh CCCD, tải công khai được từ 26/09/2026) | Người có chuyên môn pháp lý, ngay | Hồ sơ sự cố |
| **KHẨN CẤP** — Bước 0: 0a (riêng tư) hay 0b (công khai, gỡ dữ liệu + GitHub Support) | Chủ repository, ngay | Tài liệu này |
| Thời hạn giữ bản mirror mã hóa và ngày hủy | Chủ dự án | Tài liệu này |

## Kiểm tra độ đầy đủ (W6-OPS, 2026-10-03)

Đối chiếu kế hoạch với danh mục kiểm tra F21.3. Không bước nào được thực thi; không quét hay viết lại lịch sử trên remote.

| Hạng mục | Có trong kế hoạch trước đó? | Đã bổ sung |
|---|---|---|
| Kiểm kê file/đường dẫn đã lộ | Có (mục 1 + `BACKUP_CLASSIFICATION.md`) | Bảng "Kiểm kê những gì đã lộ": loại bí mật trong từng archive; kết quả tìm `.env`/credential trong lịch sử clone cục bộ; số commit/nhánh bị ảnh hưởng tới 03/10 |
| Xoay vòng cho mọi nhóm secret | Thiếu: `SEARCH_CURSOR_SECRET`, `MEDIA_SIGNING_SECRET`/`MEDIA_PREVIOUS_SIGNING_SECRET`, `RATE_LIMIT_KEY_PEPPER`, Grafana admin, role backup/exporter, IAM user backup MinIO, quyền GitHub; khóa age bị ghi "không liên quan"; khóa PII chưa nêu CCCD và bí mật TOTP | Mục 4 viết lại: 17 dòng, cột "Khi nào", cách xoay êm cho media signing, cách đổi mật khẩu Grafana sau lần khởi tạo, PII = mã hóa lại / dựng lại blind index |
| Thứ tự: xoay trước, viết lại sau | Thiếu (chỉ ghi "độc lập với phương án chọn") | Mục "Thứ tự bắt buộc" trong mục 3 |
| Công cụ (`git filter-repo`) | Có | Cách cài; lưu ý filter-repo tự gỡ remote `origin` (bước 4 trước đó push vào `origin` sẽ lỗi) |
| Force-push, clone lại | Có | Không dùng `--mirror` khi push; xóa nhánh `dependabot/*` cũ |
| Fork, ref PR | Một phần (ref PR chỉ xóa cục bộ) | Bước 7: fork với phương án C, đóng/mở lại PR |
| Yêu cầu GitHub Support xóa cache | Thiếu | Bước 4, phương án C: bắt buộc, kèm nội dung yêu cầu |
| Đối tượng LFS | Có | — |
| Kiểm chứng bằng gitleaks/trufflehog | Một phần (gitleaks một lần, không nói khi nào) | Bước 1b (trước khi viết lại, gitleaks + trufflehog không gửi dữ liệu ra ngoài) và bước 4b (clone mới sau khi đẩy, kiểm tra SHA cũ) |
| Rollback | Có (push lại mirror) | Rollback rẻ hơn cho D trước bước 5; không bao giờ rollback secret; mốc dừng |
| Trạng thái công khai của repository | Thiếu: kế hoạch giả định repository riêng tư | (Sau rà soát độc lập, 03/10/2026) mục 1: công khai từ 11/09, dữ liệu tải công khai từ 26/09; bước 0 (0a/0b) với đánh đổi; bỏ giả định "riêng tư"/"fork riêng tư"; quyết định pháp lý ở mục 5 đánh dấu khẩn cấp; image trufflehog ghim phiên bản |
