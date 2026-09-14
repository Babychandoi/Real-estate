-- Hồ sơ KYC tổng hợp dành riêng cho các tài khoản kiểm thử đã có trong stack.
-- Không chứa CCCD hay ảnh giấy tờ của người thật. Các tài khoản người dùng tạo mới
-- vẫn bắt buộc nộp đủ ba ảnh và chờ admin duyệt thủ công.
INSERT INTO user_kyc_profiles (
  id, user_id, id_number_encrypted, id_number_lookup_hash, full_name,
  dob, address, status, created_at, verified_at
)
SELECT
  ('92000000-0000-0000-0000-' || RIGHT(REPLACE(u.id::text, '-', ''), 12))::uuid,
  u.id,
  'v1:000****' || RIGHT(REPLACE(u.id::text, '-', ''), 4) || ':synthetic:synthetic',
  encode(sha256(('seed-kyc-' || u.id::text)::bytea), 'hex'),
  u.full_name,
  '01/01/1990',
  'Hồ sơ kiểm thử nội bộ',
  'VERIFIED',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM users u
WHERE u.id IN (
  '00000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000003',
  '00000000-0000-0000-0000-000000000004',
  '5276d940-34e8-4533-8bf4-e6dc73488457'
)
ON CONFLICT (user_id) DO UPDATE SET
  status = 'VERIFIED',
  rejection_reason = NULL,
  verified_at = CURRENT_TIMESTAMP;
