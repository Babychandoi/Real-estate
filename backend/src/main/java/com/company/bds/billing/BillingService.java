package com.company.bds.billing;

import com.company.bds.notification.RealtimeNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private final JdbcTemplate jdbc; private final RealtimeNotificationService notifications;
    private final JavaMailSender mailSender; private final String mailFrom;
    public BillingService(JdbcTemplate jdbc, RealtimeNotificationService notifications, JavaMailSender mailSender,
                          @Value("${app.mail.from:no-reply@bds.local}") String mailFrom) {
        this.jdbc=jdbc; this.notifications=notifications; this.mailSender=mailSender; this.mailFrom=mailFrom;
    }
    public List<Plan> plans() { return jdbc.query("SELECT code,name,price_vnd,listing_quota,duration_days,description FROM service_plans WHERE active ORDER BY sort_order", (r,n)->new Plan(r.getString(1),r.getString(2),r.getLong(3),r.getInt(4),r.getInt(5),r.getString(6))); }
    public BankSettings bank() { return jdbc.query("SELECT bank_bin,bank_name,account_number,account_name,admin_notification_email,version FROM bank_settings WHERE singleton_id=1", (r,n)->new BankSettings(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6))).stream().findFirst().orElse(null); }
    @Transactional public BankSettings saveBank(BankSettings b) {
        if (!b.bankBin().matches("\\d{6}") || !b.accountNumber().matches("\\d{6,19}")) throw new IllegalArgumentException("BIN hoặc số tài khoản không hợp lệ.");
        jdbc.update("INSERT INTO bank_settings(singleton_id,bank_bin,bank_name,account_number,account_name,admin_notification_email) VALUES(1,?,?,?,?,?) ON CONFLICT(singleton_id) DO UPDATE SET bank_bin=EXCLUDED.bank_bin,bank_name=EXCLUDED.bank_name,account_number=EXCLUDED.account_number,account_name=EXCLUDED.account_name,admin_notification_email=EXCLUDED.admin_notification_email,updated_at=CURRENT_TIMESTAMP,version=bank_settings.version+1", b.bankBin(),b.bankName(),b.accountNumber(),b.accountName(),b.adminEmail()); return bank();
    }
    @Transactional public Order create(UUID userId, String planCode) {
        Plan p=plans().stream().filter(x->x.code().equals(planCode) && x.priceVnd()>0).findFirst().orElseThrow(()->new IllegalArgumentException("Gói dịch vụ không hợp lệ."));
        BankSettings b=bank(); if(b==null) throw new IllegalStateException("Admin chưa cấu hình tài khoản nhận tiền.");
        UUID id=UUID.randomUUID(); String ref=("BDS"+id.toString().replace("-","").substring(0,12)).toUpperCase();
        jdbc.update("INSERT INTO package_orders(id,user_id,plan_code,amount_vnd,transfer_reference,status,plan_name_snapshot,quota_snapshot,duration_days_snapshot,bank_bin_snapshot,account_number_snapshot,account_name_snapshot) VALUES(?,?,?,?,?,'CREATED',?,?,?,?,?,?)",id,userId,p.code(),p.priceVnd(),ref,p.name(),p.quota(),p.durationDays(),b.bankBin(),b.accountNumber(),b.accountName());
        return load(id,userId,false);
    }
    @Transactional public Order report(UUID id, UUID userId) {
        int changed=jdbc.update("UPDATE package_orders SET status='TRANSFER_REPORTED',user_reported_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND user_id=? AND status='CREATED'",id,userId);
        Order order=load(id,userId,false); if(changed>0){ notifications.notify(userId,"PAYMENT_REPORTED","Đã gửi đối soát","Yêu cầu "+order.reference()+" đã chuyển đến quản trị viên."); emailAdmin(order); } return order;
    }
    @Transactional public Order approve(UUID id, UUID adminId, String note) {
        Order order=load(id,null,true); int changed=jdbc.update("UPDATE package_orders SET status='APPROVED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=?,version=version+1 WHERE id=? AND status='TRANSFER_REPORTED'",adminId,note,id);
        if(changed==0) return load(id,null,true);
        Plan p=plans().stream().filter(x->x.code().equals(order.planCode())).findFirst().orElseThrow();
        jdbc.update("UPDATE users SET plan_code=?,plan_expires_at=GREATEST(COALESCE(plan_expires_at,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP)+(?||' days')::interval,listing_quota_remaining=listing_quota_remaining+?,updated_at=CURRENT_TIMESTAMP WHERE id=?",p.code(),p.durationDays(),p.quota(),order.userId());
        jdbc.update("INSERT INTO invoices(id,invoice_number,order_id,user_id,amount_vnd) VALUES(?,?,?,?,?)",UUID.randomUUID(),"INV-"+java.time.LocalDate.now()+"-"+order.reference(),id,order.userId(),order.amountVnd());
        notifications.notify(order.userId(),"PLAN_UPGRADED","Nâng cấp thành công","Tài khoản đã được nâng lên gói "+p.name()+" và có thêm "+p.quota()+" lượt đăng tin.");
        return load(id,null,true);
    }
    @Transactional public Order reject(UUID id, UUID adminId, String reason) {
        if(reason==null||reason.isBlank()) throw new IllegalArgumentException("Cần nhập lý do từ chối đối soát.");
        Order order=load(id,null,true);
        int changed=jdbc.update("UPDATE package_orders SET status='REJECTED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=?,version=version+1 WHERE id=? AND status='TRANSFER_REPORTED'",adminId,reason.trim(),id);
        if(changed>0) notifications.notify(order.userId(),"PAYMENT_REJECTED","Thanh toán cần kiểm tra lại",reason.trim());
        return load(id,null,true);
    }
    @Transactional public Order cancel(UUID id, UUID userId) {
        jdbc.update("UPDATE package_orders SET status='CANCELLED',reviewed_at=CURRENT_TIMESTAMP,review_note='Người dùng đã hủy',version=version+1 WHERE id=? AND user_id=? AND status='CREATED'",id,userId);
        return load(id,userId,false);
    }
    public List<Order> mine(UUID userId){return query("WHERE o.user_id=? ORDER BY o.created_at DESC",userId);}
    public List<Order> queue(){return query("WHERE o.status='TRANSFER_REPORTED' ORDER BY o.user_reported_at",new Object[0]);}
    public AdminOrderPage adminOrders(int page, int size, String status, String keyword) {
        int safePage=Math.max(0,page), safeSize=Math.min(100,Math.max(1,size));
        String normalizedStatus=status==null?"":status.trim().toUpperCase();
        String normalizedKeyword=keyword==null?"":keyword.trim();
        String predicates=" WHERE (?='' OR o.status=?) AND (?='' OR o.transfer_reference ILIKE ? OR u.full_name ILIKE ? OR u.email ILIKE ?)";
        String like="%"+normalizedKeyword+"%";
        long total=jdbc.queryForObject("SELECT count(*) FROM package_orders o JOIN users u ON u.id=o.user_id"+predicates,Long.class,normalizedStatus,normalizedStatus,normalizedKeyword,like,like,like);
        List<AdminOrder> items=jdbc.query("SELECT o.id,o.user_id,u.full_name,u.email,o.plan_code,o.plan_name_snapshot,o.amount_vnd,o.transfer_reference,o.status,o.created_at,o.user_reported_at,o.reviewed_at,o.review_note FROM package_orders o JOIN users u ON u.id=o.user_id"+predicates+" ORDER BY o.created_at DESC LIMIT ? OFFSET ?",(r,n)->new AdminOrder(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getLong(7),r.getString(8),r.getString(9),r.getTimestamp(10).toInstant(),r.getTimestamp(11)==null?null:r.getTimestamp(11).toInstant(),r.getTimestamp(12)==null?null:r.getTimestamp(12).toInstant(),r.getString(13)),normalizedStatus,normalizedStatus,normalizedKeyword,like,like,like,safeSize,safePage*safeSize);
        return new AdminOrderPage(items,safePage,safeSize,total);
    }
    private List<Order> query(String where,Object... args){return jdbc.query("SELECT o.id,o.user_id,o.plan_code,o.amount_vnd,o.transfer_reference,o.status,o.created_at,b.bank_bin,b.account_number,b.account_name FROM package_orders o LEFT JOIN bank_settings b ON b.singleton_id=1 "+where,(r,n)->map(r),args);}
    private Order load(UUID id,UUID userId,boolean admin){String where=admin?"WHERE o.id=?":"WHERE o.id=? AND o.user_id=?"; return query(where,admin?new Object[]{id}:new Object[]{id,userId}).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Không tìm thấy yêu cầu thanh toán."));}
    private Order map(java.sql.ResultSet r)throws java.sql.SQLException{String qr=null;if(r.getString(8)!=null)qr="https://img.vietqr.io/image/"+r.getString(8)+"-"+r.getString(9)+"-compact2.png?amount="+r.getLong(4)+"&addInfo="+enc(r.getString(5))+"&accountName="+enc(r.getString(10));return new Order(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getTimestamp(7).toInstant(),qr);}
    private static String enc(String v){return URLEncoder.encode(v,StandardCharsets.UTF_8);}
    private void emailAdmin(Order o) {
        BankSettings b=bank();
        if(b==null||b.adminEmail()==null||b.adminEmail().isBlank()) return;
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(b.adminEmail());
        message.setSubject("[Nhà Đất Chuẩn] Có thanh toán chờ đối soát " + o.reference());
        message.setText("Mã đối soát: " + o.reference() + "\nSố tiền: " + o.amountVnd()
                + " VND\nVui lòng mở trang quản trị để kiểm tra và xác nhận.");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Không thể gửi email đối soát cho đơn {}", o.id(), ex);
        }
    }
    public record Plan(String code,String name,long priceVnd,int quota,int durationDays,String description){}
    public record BankSettings(String bankBin,String bankName,String accountNumber,String accountName,String adminEmail,long version){}
    public record Order(UUID id,UUID userId,String planCode,long amountVnd,String reference,String status,Instant createdAt,String qrUrl){}
    public record AdminOrder(UUID id,UUID userId,String customerName,String customerEmail,String planCode,String planName,long amountVnd,String reference,String status,Instant createdAt,Instant reportedAt,Instant reviewedAt,String reviewNote){}
    public record AdminOrderPage(List<AdminOrder> items,int page,int size,long total){}
}
