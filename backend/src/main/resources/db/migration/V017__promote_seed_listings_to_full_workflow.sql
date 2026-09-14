-- Chuyển bộ dữ liệu khởi tạo thành dữ liệu kiểm thử toàn luồng.
-- Trên môi trường hiện tại, gán các tin cho tài khoản broker vận hành để tài khoản đó
-- nhận/reveal lead và quản lý tin như mọi tin được tạo qua ứng dụng.
UPDATE listings
SET owner_id = owner.id,
    updated_at = CURRENT_TIMESTAMP
FROM users owner
WHERE owner.email = 'phonglop7d@gmail.com'
  AND listings.id IN (
    '71000000-0000-0000-0000-000000000001',
    '71000000-0000-0000-0000-000000000002',
    '71000000-0000-0000-0000-000000000003',
    '71000000-0000-0000-0000-000000000004',
    '71000000-0000-0000-0000-000000000005',
    '71000000-0000-0000-0000-000000000006'
  );

UPDATE listing_revisions
SET description = CASE listing_id
  WHEN '71000000-0000-0000-0000-000000000001' THEN 'Căn hộ hai phòng ngủ, ban công thoáng, nội thất cơ bản, thuận tiện di chuyển tới Mỹ Đình và khu trung tâm.'
  WHEN '71000000-0000-0000-0000-000000000002' THEN 'Căn hộ ba phòng ngủ phù hợp gia đình, gần công viên Cầu Giấy, khu dân cư có đầy đủ tiện ích.'
  WHEN '71000000-0000-0000-0000-000000000003' THEN 'Nhà riêng bốn tầng trong khu dân cư, đường đi thuận tiện; người xem cần kiểm tra hiện trạng và hồ sơ pháp lý.'
  WHEN '71000000-0000-0000-0000-000000000004' THEN 'Căn hộ cho thuê dài hạn, hai phòng ngủ, nội thất cơ bản; giá thuê chưa bao gồm phí dịch vụ.'
  WHEN '71000000-0000-0000-0000-000000000005' THEN 'Nhà phố phù hợp để ở kết hợp làm văn phòng nhỏ; liên hệ trực tiếp để xác minh điều kiện thuê.'
  WHEN '71000000-0000-0000-0000-000000000006' THEN 'Lô đất đường ô tô, khu dân cư hiện hữu; người quan tâm cần kiểm tra quy hoạch và giấy tờ tại cơ quan có thẩm quyền.'
  ELSE description
END,
moderation_note = 'Nội dung đã được quản trị viên phê duyệt.'
WHERE listing_id IN (
  '71000000-0000-0000-0000-000000000001',
  '71000000-0000-0000-0000-000000000002',
  '71000000-0000-0000-0000-000000000003',
  '71000000-0000-0000-0000-000000000004',
  '71000000-0000-0000-0000-000000000005',
  '71000000-0000-0000-0000-000000000006'
);
