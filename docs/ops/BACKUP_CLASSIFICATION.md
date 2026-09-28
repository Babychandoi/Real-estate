# Phân loại bản sao lưu trong Git LFS và quy tắc sao lưu từ nay

Yêu cầu audit F21.1 (phân loại backup) và nền tảng cho F21.2/F21.3. Tài liệu này chỉ dựa trên
`backups/20260926-005833/README.md`, lịch sử Git và mã nguồn đang có; **không mở, giải nén hay tải nội dung các archive**.

## 1. Bản sao lưu đang nằm trong repository

| Thuộc tính | Giá trị (nguồn) |
|---|---|
| Thư mục | `backups/20260926-005833/` |
| Commit | `2d226a4` "backup: add local data snapshot 2026-09-26", 26/09/2026 01:12 (+07) |
| Đã đẩy lên GitHub | Có: commit nằm trong `origin/main` và `origin/feat/macos-uat-compare-map-profiles` (`git branch -r --contains 2d226a4`) |
| Cách lưu | Git LFS (`.gitattributes`: `backups/20260926-005833/*.tar.gz filter=lfs`); trong Git chỉ có file con trỏ, nội dung nằm ở kho LFS của GitHub và trong `.git/lfs` của mọi bản clone đã tải LFS |
| Bản chất (README) | "raw volume snapshots, not logical exports", tạo từ các Docker volume đã dừng; README tự khuyến cáo coi PostgreSQL và MinIO là nhạy cảm |
| Nguồn dữ liệu | Theo yêu cầu của đợt audit: volume **production** (PostgreSQL, MinIO gồm ảnh KYC, Redis, Elasticsearch) |

| Archive | Kích thước (README) | Nội dung theo cách hệ thống dùng volume đó |
|---|---:|---|
| `postgres-data.tar.gz` | 17.519.169 B | Toàn bộ `PGDATA`: bảng nghiệp vụ, WAL (có thể còn dữ liệu vừa xóa), `pg_authid` (verifier SCRAM của mật khẩu database) |
| `minio-data.tar.gz` | 9.096.579 B | Mọi object của bucket `bds-listings` — ảnh tin, avatar **và ảnh KYC** (CCCD mặt trước/sau, ảnh chân dung; phân biệt bằng `media_objects.visibility = 'KYC_PRIVATE'`, cùng một bucket) — cùng cấu hình IAM trong `.minio.sys` |
| `redis-data.tar.gz` | 2.101 B | AOF của Redis: bộ đếm rate limit phiên bản cũ (khóa băm 32 bit từ IP + đường dẫn), cache |
| `elasticsearch-data.tar.gz` | 98.256 B | Chỉ mục `bds-listings` dựng từ tin công khai |
| `clamav-data.tar.gz` | 215.460.681 B | Cơ sở dữ liệu chữ ký virus công khai của ClamAV |

Những gì volume PostgreSQL của hệ thống này chứa (theo schema Flyway V001–V026): email, họ tên, băm bcrypt của mật
khẩu, số điện thoại mã hóa AES-GCM (`phone_encrypted`) và blind index HMAC, hồ sơ KYC và đường dẫn ảnh giấy tờ, thông tin
liên hệ trong lead, đơn gói dịch vụ và tài khoản ngân hàng nhận tiền, nhật ký audit, nội dung thông báo; token phiên,
xác minh email, đặt lại mật khẩu chỉ ở dạng băm SHA-256.

## 2. Phân loại

| Mức | Định nghĩa dùng trong dự án |
|---|---|
| **Mật – dữ liệu cá nhân** | Dữ liệu cá nhân hoặc giấy tờ định danh của người dùng thật; lộ ra gây thiệt hại cho người dùng và nghĩa vụ pháp lý cho đơn vị vận hành |
| **Mật** | Bí mật vận hành (mật khẩu, khóa, verifier), dữ liệu kinh doanh không công khai |
| **Nội bộ** | Không chứa dữ liệu cá nhân, không công khai (số liệu vận hành, chỉ mục dựng lại được) |
| **Công khai** | Có thể công bố |

| Archive | Mức | Lý do |
|---|---|---|
| `postgres-data.tar.gz` | **Mật – dữ liệu cá nhân** | Email, họ tên, băm mật khẩu, bản mã số điện thoại, hồ sơ KYC, lead; verifier mật khẩu database |
| `minio-data.tar.gz` | **Mật – dữ liệu cá nhân (nhạy cảm nhất)** | Ảnh giấy tờ tùy thân và ảnh chân dung không mã hóa |
| `redis-data.tar.gz` | Mật (cho tới khi được xác minh) | Bộ đếm gắn với IP khách; không kiểm tra nội dung nên không hạ mức |
| `elasticsearch-data.tar.gz` | Nội bộ | Dữ liệu tin đã công khai |
| `clamav-data.tar.gz` | Công khai | Chữ ký virus công khai |
| `README.md` | Nội bộ | Mô tả, kích thước, checksum |

**Cả thư mục được xử lý ở mức cao nhất (Mật – dữ liệu cá nhân)** vì các archive đi chung một commit, chung quyền truy
cập, và không thể thu hồi riêng từng file khỏi các bản clone.

## 3. Ai đang có thể truy cập

Không cần thao tác gì thêm, những bên sau đã hoặc có thể lấy được nội dung:

- Mọi tài khoản có quyền đọc repository `Babychandoi/Real-estate` trên GitHub (collaborator, thành viên/owner của
  tổ chức, team được cấp quyền): `git lfs pull` tải toàn bộ archive.
- Ứng dụng GitHub, deploy key, token cá nhân có quyền `contents:read` trên repository.
- Mọi fork của repository và bản clone của fork: lịch sử chứa commit backup; coi như đã có thể có bản sao archive.
- Mọi bản clone đã tải LFS: máy phát triển đang chạy production (`/Users/connecty/Real-estate` và các worktree dùng
  chung `.git`), máy của cộng tác viên, và bản sao lưu của các máy đó (Time Machine, iCloud, ổ ngoài...).
- GitHub Actions hiện **không** tải LFS (`actions/checkout` không bật `lfs: true`), nên log và artifact CI không chứa
  archive; điều này phải được giữ nguyên.

Lệnh để chủ repo lập danh sách chính xác (cần quyền admin):

```bash
gh api repos/Babychandoi/Real-estate/collaborators --paginate --jq '.[] | [.login, .role_name] | @tsv'
gh api repos/Babychandoi/Real-estate/keys --jq '.[] | [.id, .title, .read_only] | @tsv'
gh api repos/Babychandoi/Real-estate/forks --jq '.[] | .full_name'
```

Ứng dụng GitHub được cài cho repository xem ở Settings → Integrations → GitHub Apps.

## 4. Rủi ro

| Rủi ro | Mô tả | Mức |
|---|---|---|
| Lộ giấy tờ tùy thân | Ảnh CCCD/chân dung đọc được trực tiếp từ volume MinIO; không có lớp mã hóa nào | Cao |
| Lộ dữ liệu cá nhân | Email, họ tên, lead; số điện thoại chỉ an toàn khi `PII_ENCRYPTION_KEY` không bị lộ (khóa ở `.env`, không nằm trong volume) | Cao |
| Bẻ khóa mật khẩu ngoại tuyến | Băm bcrypt (cost 12) của mọi tài khoản; người dùng dùng lại mật khẩu ở nơi khác bị ảnh hưởng | Trung bình–cao |
| Bí mật hạ tầng | Verifier SCRAM của mật khẩu PostgreSQL; cấu hình IAM của MinIO | Trung bình |
| Không thu hồi được | Clone và fork đã tồn tại không thể xóa từ xa; đối tượng LFS trên GitHub chỉ mất khi repository bị xóa và tạo lại | Cao |
| Pháp lý | Có thể phát sinh nghĩa vụ theo Nghị định 13/2023/NĐ-CP về bảo vệ dữ liệu cá nhân (dữ liệu nhạy cảm, đánh giá và thông báo sự cố). Tài liệu này không kết luận pháp lý; cần người có chuyên môn pháp lý đánh giá | Cần quyết định |
| Toàn vẹn/khôi phục | Snapshot volume thô chỉ khôi phục được với đúng phiên bản dịch vụ; chưa từng được diễn tập | Trung bình |

## 5. Việc cần chủ dự án quyết định (EXTERNAL)

1. Rà soát và thu hẹp quyền đọc repository (mục 3).
2. Chọn phương án xử lý lịch sử Git: `docs/ops/GIT_HISTORY_REMEDIATION_PLAN.md` (không agent nào tự viết lại lịch sử).
3. Xoay vòng các bí mật và yêu cầu đặt lại mật khẩu theo danh sách kiểm tra trong kế hoạch đó.
4. Nhờ người có chuyên môn pháp lý đánh giá nghĩa vụ đối với dữ liệu cá nhân và ảnh KYC.

## 6. Quy tắc sao lưu từ nay (đã có công cụ)

| Quy tắc | Cách được bảo đảm |
|---|---|
| Không đưa bản sao lưu vào Git | `.gitignore` có `backups/` và `*.dump.age`; `scripts/restore-drill.sh` từ chối `BACKUP_DIR` nằm trong repository |
| Mã hóa trước khi ghi đĩa | `infra/backup` stream `pg_dump` và từng object MinIO thẳng vào `age`; bản rõ không chạm đĩa |
| Khóa tách khỏi dữ liệu | Dịch vụ backup chỉ có **public key** (`BACKUP_AGE_RECIPIENTS`); private key giữ ngoại tuyến bởi chủ hệ thống, chỉ mang vào lúc khôi phục/diễn tập |
| Quyền truy cập | File 0600, thư mục 0700 (umask 077), container không root, `read_only`, không capability; `BACKUP_DIR` nên là ổ mã hóa hoặc được đồng bộ ra ngoài máy chủ |
| Toàn vẹn | Manifest có SHA-256 từng file mã hóa, số dòng từng bảng lấy trong cùng snapshot, `SUCCESS` ghi sau cùng; `restore.sh` kiểm tra checksum trước khi giải mã |
| Lưu giữ | DB: mọi bản trong 48 giờ, mỗi ngày một bản trong 14 ngày; object: mỗi ngày trong 14 ngày; luôn giữ ít nhất 3 set (`infra/compose.backup.yaml`) |
| Kiểm chứng | `scripts/restore-drill.sh` khôi phục vào project cô lập `bds-drill`, đối soát và ghi biên bản `docs/ops/drills/`; lần đầu: `docs/ops/drills/2026-09-27-synthetic.md` (dữ liệu tổng hợp) |
| Giám sát | Metrics `bds_backup_*` + cảnh báo `BdsDatabaseBackupStale`, `BdsBackupFailed`, `BdsBackupNeverReported` |

Phân loại của bản sao lưu mới: manifest (tên bảng, số dòng, checksum, public key) là **Nội bộ**; `index.jsonl` của
object (khóa object, kích thước, SHA-256 bản rõ) là **Nội bộ**; mọi file `.age` là **Mật – dữ liệu cá nhân** dù đã mã hóa,
vì chỉ an toàn chừng nào private key còn an toàn.
