import type { BadgeVariant } from '@/shared/ui/Badge';
import type { OrderStatus } from './types';

/** Order states as the user and staff read them (label + what happens next). */
export const ORDER_STATUS: Record<OrderStatus, { label: string; hint: string; variant: BadgeVariant }> = {
  CREATED: {
    label: 'Chờ chuyển khoản',
    hint: 'Chuyển khoản đúng số tiền và nội dung, rồi bấm "Tôi đã chuyển khoản".',
    variant: 'info',
  },
  TRANSFER_REPORTED: {
    label: 'Đang chờ đối soát',
    hint: 'Quản trị viên đang đối chiếu với sao kê ngân hàng.',
    variant: 'warning',
  },
  EXCEPTION: {
    label: 'Cần đối chiếu thêm',
    hint: 'Khoản nhận chưa khớp số tiền hoặc nội dung; quản trị viên sẽ liên hệ.',
    variant: 'warning',
  },
  APPROVED: { label: 'Đã kích hoạt', hint: 'Lượt đăng đã được cộng vào tài khoản.', variant: 'success' },
  REJECTED: { label: 'Không được duyệt', hint: 'Xem lý do bên dưới.', variant: 'error' },
  REFUNDED: { label: 'Đã hoàn tiền', hint: 'Khoản chuyển đã được hoàn lại ngoài hệ thống.', variant: 'neutral' },
  CANCELLED: { label: 'Đã hủy', hint: 'Bạn đã hủy yêu cầu này.', variant: 'neutral' },
};
