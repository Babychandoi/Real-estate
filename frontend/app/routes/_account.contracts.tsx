import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ShieldCheck, Lock, CheckCircle2, AlertTriangle, FileText,
  UserCheck, Smartphone, DollarSign, History, KeyRound, ArrowLeft
} from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Badge } from '@/shared/ui/Badge';
import { transactionApi } from '@/entities/transaction/api/transactionApi';
import { DepositContract } from '@/entities/transaction/model/types';
import { formatPriceVnd } from '@/entities/listing/model/types';

export const DepositContractPage: React.FC = () => {
  const { contractId } = useParams<{ contractId: string }>();
  const [contract, setContract] = useState<DepositContract | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [otpCode, setOtpCode] = useState('123456');
  const [actionLoading, setActionLoading] = useState(false);
  const [notification, setNotification] = useState<string | null>(null);

  useEffect(() => {
    loadContract();
  }, [contractId]);

  const loadContract = async () => {
    if (!contractId) {
      // Mock contract nếu truy cập trực tiếp demo
      setContract({
        id: 'dc-8899-2026',
        listingId: 'lst-101',
        buyerId: 'usr-buy-1',
        buyerName: 'Nguyễn Văn Hùng',
        buyerPhone: '0912 345 678',
        buyerIdMasked: '001****4567',
        sellerId: 'usr-sell-2',
        sellerName: 'Trần Thị Thu Thảo',
        sellerPhone: '0988 889 999',
        depositAmount: 100000000,
        listingPrice: 4200000000,
        status: 'AWAITING_SELLER_SIGN',
        termsConditions: 'Bên mua đặt cọc 100.000.000 VNĐ qua Ký quỹ Escrow bảo đảm BDS WF 2026. Trong 15 ngày kể từ ngày ký, hai bên tiến hành công chứng chuyển nhượng căn hộ. Tiền cọc được hệ thống phong tỏa bảo đảm cho tới khi thủ tục hoàn tất.',
        buyerSignedAt: new Date(Date.now() - 3600000).toISOString(),
        buyerOtpVerified: true,
        sellerSignedAt: undefined,
        sellerOtpVerified: false,
        escrowLockedAt: undefined,
        completedAt: undefined,
        createdAt: new Date(Date.now() - 7200000).toISOString(),
        updatedAt: new Date(Date.now() - 3600000).toISOString(),
        escrowTransactions: [
          {
            id: 'tx-1',
            action: 'DEPOSIT',
            amount: 100000000,
            performedBy: 'usr-buy-1',
            note: 'Người mua nạp tiền cọc và ký số hợp đồng qua OTP',
            createdAt: new Date(Date.now() - 3600000).toISOString(),
          },
        ],
      });
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const res = await transactionApi.getContract(contractId);
      setContract(res);
    } catch (err: unknown) {
      console.error(err);
      setError('Không thể tải thông tin hợp đồng đặt cọc');
    } finally {
      setLoading(false);
    }
  };

  const handleSignBuyer = async () => {
    if (!contract) return;
    setActionLoading(true);
    setNotification(null);
    try {
      const updated = await transactionApi.signBuyer(contract.id, { otpCode });
      setContract(updated);
      setNotification('Người mua đã ký số thành công bằng OTP!');
    } catch (err: unknown) {
      console.error(err);
      // Demo mock fallback
      setContract(prev => prev ? {
        ...prev,
        status: 'AWAITING_SELLER_SIGN',
        buyerOtpVerified: true,
        buyerSignedAt: new Date().toISOString(),
      } : null);
      setNotification('Người mua đã ký số thành công bằng OTP (Chế độ mô phỏng)!');
    } finally {
      setActionLoading(false);
    }
  };

  const handleSignSeller = async () => {
    if (!contract) return;
    setActionLoading(true);
    setNotification(null);
    try {
      const updated = await transactionApi.signSeller(contract.id, { otpCode });
      setContract(updated);
      setNotification('Bên bán đã ký số xác nhận! Tiền cọc đã được PHONG TỎA VÀO ESCROW VAULT an toàn!');
    } catch (err: unknown) {
      console.error(err);
      setContract(prev => prev ? {
        ...prev,
        status: 'ESCROW_LOCKED',
        sellerOtpVerified: true,
        sellerSignedAt: new Date().toISOString(),
        escrowLockedAt: new Date().toISOString(),
        escrowTransactions: [
          ...(prev.escrowTransactions || []),
          {
            id: 'tx-2',
            action: 'LOCK',
            amount: prev.depositAmount,
            performedBy: prev.sellerId,
            note: 'Bên bán ký số - Đã phong tỏa bảo đảm trong Escrow Vault',
            createdAt: new Date().toISOString(),
          },
        ],
      } : null);
      setNotification('Bên bán đã ký số xác nhận! Tiền cọc đã được PHONG TỎA VÀO ESCROW VAULT an toàn (Chế độ mô phỏng)!');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRelease = async () => {
    if (!contract) return;
    setActionLoading(true);
    setNotification(null);
    try {
      const updated = await transactionApi.releaseEscrow(contract.id);
      setContract(updated);
      setNotification('Đã hoàn tất giải ngân tiền cọc cho Bên Bán sau công chứng!');
    } catch (err: unknown) {
      console.error(err);
      setContract(prev => prev ? {
        ...prev,
        status: 'COMPLETED',
        completedAt: new Date().toISOString(),
        escrowTransactions: [
          ...(prev.escrowTransactions || []),
          {
            id: 'tx-3',
            action: 'RELEASE',
            amount: prev.depositAmount,
            performedBy: 'admin-system',
            note: 'Giải ngân thành công cho bên bán',
            createdAt: new Date().toISOString(),
          },
        ],
      } : null);
      setNotification('Đã hoàn tất giải ngân tiền cọc cho Bên Bán sau công chứng (Mô phỏng)!');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRefund = async () => {
    if (!contract) return;
    setActionLoading(true);
    setNotification(null);
    try {
      const updated = await transactionApi.refundEscrow(contract.id, 'Thỏa thuận hủy cọc / vi phạm cam kết pháp lý');
      setContract(updated);
      setNotification('Đã hoàn trả 100% tiền cọc về tài khoản Bên Mua!');
    } catch (err: unknown) {
      console.error(err);
      setContract(prev => prev ? {
        ...prev,
        status: 'REFUNDED',
        disputeReason: 'Hủy cọc hợp lệ',
        escrowTransactions: [
          ...(prev.escrowTransactions || []),
          {
            id: 'tx-4',
            action: 'REFUND',
            amount: prev.depositAmount,
            performedBy: 'admin-system',
            note: 'Hoàn trả tiền cọc cho bên mua',
            createdAt: new Date().toISOString(),
          },
        ],
      } : null);
      setNotification('Đã hoàn trả 100% tiền cọc về tài khoản Bên Mua (Mô phỏng)!');
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="container mx-auto px-4 py-16 text-center">
        <div className="w-12 h-12 border-4 border-emerald-600 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
        <p className="text-slate-600 font-medium">Đang tải chi tiết hợp đồng ký số Escrow...</p>
      </div>
    );
  }

  if (error || !contract) {
    return (
      <div className="container mx-auto px-4 py-16 text-center">
        <AlertTriangle className="w-12 h-12 text-amber-500 mx-auto mb-3" />
        <p className="text-slate-800 font-semibold mb-4">{error || 'Không tìm thấy hợp đồng'}</p>
        <Link to="/search">
          <Button variant="outline">Quay lại tìm kiếm</Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 py-8">
      <div className="container mx-auto px-4 max-w-6xl">
        {/* Navigation & Header */}
        <div className="flex items-center justify-between mb-6">
          <Link to={`/listings/${contract.listingId}`} className="inline-flex items-center gap-2 text-slate-600 hover:text-slate-900 transition-colors font-medium text-sm">
            <ArrowLeft className="w-4 h-4" />
            <span>Quay lại chi tiết BĐS</span>
          </Link>
          <Badge variant="neutral" className="bg-emerald-50 text-emerald-700 border-emerald-200">
            Hợp đồng Điện tử An toàn • ISO/IEC 29148
          </Badge>
        </div>

        {/* Title Bar */}
        <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-200 mb-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-xl bg-emerald-600 flex items-center justify-center text-white shrink-0 shadow-md">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-3">
                <h1 className="text-xl md:text-2xl font-bold text-slate-900">
                  Hợp đồng Đặt cọc Ký quỹ Escrow Bảo đảm
                </h1>
                {contract.status === 'ESCROW_LOCKED' && (
                  <Badge className="bg-emerald-600 text-white font-semibold">
                    <Lock className="w-3 h-3 mr-1 inline" /> ĐÃ PHONG TỎA KÝ QUỸ
                  </Badge>
                )}
                {contract.status === 'AWAITING_SELLER_SIGN' && (
                  <Badge className="bg-amber-500 text-white font-semibold">
                    CHỜ BÊN BÁN KÝ
                  </Badge>
                )}
                {contract.status === 'DRAFT' && (
                  <Badge className="bg-slate-400 text-white font-semibold">
                    BẢN NHÁP
                  </Badge>
                )}
                {contract.status === 'COMPLETED' && (
                  <Badge className="bg-blue-600 text-white font-semibold">
                    ĐÃ GIẢI NGÂN HOÀN TẤT
                  </Badge>
                )}
                {contract.status === 'REFUNDED' && (
                  <Badge className="bg-rose-600 text-white font-semibold">
                    ĐÃ HOÀN CỌC
                  </Badge>
                )}
              </div>
              <p className="text-sm text-slate-500 mt-1">
                Mã hợp đồng: <span className="font-mono text-slate-700 font-semibold">{contract.id}</span> • Liên kết tin đăng: <span className="font-mono text-slate-700 font-semibold">{contract.listingId}</span>
              </p>
            </div>
          </div>

          <div className="flex flex-col items-end">
            <span className="text-xs text-slate-500">Số tiền đặt cọc bảo đảm</span>
            <span className="text-2xl font-black text-emerald-700">
              {formatPriceVnd(contract.depositAmount)}
            </span>
            <span className="text-xs text-slate-400">
              (Giá thỏa thuận BĐS: {formatPriceVnd(contract.listingPrice)})
            </span>
          </div>
        </div>

        {/* Thông báo thao tác */}
        {notification && (
          <div className="p-4 mb-6 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-900 flex items-center gap-3">
            <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
            <span className="font-medium text-sm">{notification}</span>
          </div>
        )}

        {/* Tiến trình Waterfall 4 Bước */}
        <div className="bg-white rounded-2xl p-6 shadow-sm border border-slate-200 mb-6">
          <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">
            Quy trình Giao dịch Bảo đảm 4 Giai đoạn (BR03 / FR28)
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
            <div className={`p-3 rounded-xl border flex items-center gap-3 ${
              contract.buyerOtpVerified ? 'bg-emerald-50 border-emerald-300 text-emerald-900' : 'bg-slate-50 border-slate-200 text-slate-500'
            }`}>
              <UserCheck className="w-5 h-5 text-emerald-600" />
              <div>
                <p className="text-xs font-bold">1. Người mua Ký OTP</p>
                <p className="text-[11px] text-slate-500">{contract.buyerOtpVerified ? 'Đã ký xác nhận' : 'Chưa ký'}</p>
              </div>
            </div>

            <div className={`p-3 rounded-xl border flex items-center gap-3 ${
              contract.sellerOtpVerified ? 'bg-emerald-50 border-emerald-300 text-emerald-900' : 'bg-slate-50 border-slate-200 text-slate-500'
            }`}>
              <Smartphone className="w-5 h-5 text-emerald-600" />
              <div>
                <p className="text-xs font-bold">2. Người bán Ký OTP</p>
                <p className="text-[11px] text-slate-500">{contract.sellerOtpVerified ? 'Đã ký xác nhận' : 'Chờ ký xác nhận'}</p>
              </div>
            </div>

            <div className={`p-3 rounded-xl border flex items-center gap-3 ${
              contract.status === 'ESCROW_LOCKED' || contract.status === 'COMPLETED'
                ? 'bg-emerald-50 border-emerald-300 text-emerald-900'
                : 'bg-slate-50 border-slate-200 text-slate-500'
            }`}>
              <Lock className="w-5 h-5 text-emerald-600" />
              <div>
                <p className="text-xs font-bold">3. Phong tỏa Escrow</p>
                <p className="text-[11px] text-slate-500">
                  {contract.escrowLockedAt ? 'Đang bảo đảm an toàn' : 'Chờ kích hoạt'}
                </p>
              </div>
            </div>

            <div className={`p-3 rounded-xl border flex items-center gap-3 ${
              contract.status === 'COMPLETED'
                ? 'bg-blue-50 border-blue-300 text-blue-900'
                : 'bg-slate-50 border-slate-200 text-slate-500'
            }`}>
              <DollarSign className="w-5 h-5 text-blue-600" />
              <div>
                <p className="text-xs font-bold">4. Giải ngân / Công chứng</p>
                <p className="text-[11px] text-slate-500">
                  {contract.status === 'COMPLETED' ? 'Đã hoàn tất giao dịch' : 'Chờ hoàn tất'}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* 2 CỘT CHÍNH: HỢP ĐỒNG PHÁP LÝ & BÀN ĐIỀU KHIỂN ESCROW */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* CỘT TRÁI (7 cols): Điều khoản hợp đồng & Thông tin 2 bên */}
          <div className="lg:col-span-7 flex flex-col gap-6">
            <Card className="p-6">
              <div className="flex items-center gap-2 mb-4 pb-3 border-b border-slate-100">
                <FileText className="w-5 h-5 text-emerald-600" />
                <h2 className="text-base font-bold text-slate-900">Chi tiết Điều khoản Thỏa thuận Cọc</h2>
              </div>

              {/* Thông tin 2 bên */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-emerald-700 uppercase">Bên Mua (Bên Đặt Cọc)</span>
                    {contract.buyerOtpVerified && (
                      <span className="text-[10px] font-bold text-emerald-600 bg-emerald-100 px-1.5 py-0.5 rounded">
                        Đã Ký OTP
                      </span>
                    )}
                  </div>
                  <p className="font-bold text-slate-900">{contract.buyerName}</p>
                  <p className="text-xs text-slate-500 mt-0.5">SĐT: {contract.buyerPhone}</p>
                  <p className="text-xs text-slate-500">CCCD: <span className="font-mono font-semibold">{contract.buyerIdMasked}</span></p>
                </div>

                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-blue-700 uppercase">Bên Bán (Bên Nhận Cọc)</span>
                    {contract.sellerOtpVerified && (
                      <span className="text-[10px] font-bold text-blue-600 bg-blue-100 px-1.5 py-0.5 rounded">
                        Đã Ký OTP
                      </span>
                    )}
                  </div>
                  <p className="font-bold text-slate-900">{contract.sellerName}</p>
                  <p className="text-xs text-slate-500 mt-0.5">SĐT: {contract.sellerPhone}</p>
                  <p className="text-xs text-slate-500">Chính chủ eKYC: <span className="text-emerald-600 font-bold">Đã xác minh ✓</span></p>
                </div>
              </div>

              {/* Nội dung điều khoản cam kết */}
              <div className="p-4 rounded-xl bg-amber-50/50 border border-amber-200/80 mb-4">
                <h4 className="text-xs font-bold text-amber-900 uppercase mb-2">Cam kết pháp lý bắt buộc (FR30 / Luật Kinh doanh BĐS)</h4>
                <p className="text-xs text-slate-700 leading-relaxed whitespace-pre-line font-serif">
                  {contract.termsConditions}
                </p>
              </div>

              {/* Lịch sử kiểm toán Escrow */}
              <div>
                <h4 className="text-xs font-bold text-slate-500 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                  <History className="w-3.5 h-3.5" />
                  Nhật ký Biến động Ký quỹ (FR28 Audit Trail)
                </h4>
                <div className="space-y-2">
                  {contract.escrowTransactions && contract.escrowTransactions.length > 0 ? (
                    contract.escrowTransactions.map((tx) => (
                      <div key={tx.id} className="p-2.5 rounded-lg bg-slate-50 border border-slate-200 text-xs flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className={`px-1.5 py-0.5 rounded font-bold text-[10px] ${
                            tx.action === 'LOCK' ? 'bg-emerald-100 text-emerald-800' :
                            tx.action === 'RELEASE' ? 'bg-blue-100 text-blue-800' :
                            tx.action === 'REFUND' ? 'bg-rose-100 text-rose-800' : 'bg-slate-200 text-slate-800'
                          }`}>
                            {tx.action}
                          </span>
                          <span className="text-slate-700">{tx.note}</span>
                        </div>
                        <span className="font-bold text-emerald-700">
                          {formatPriceVnd(tx.amount)}
                        </span>
                      </div>
                    ))
                  ) : (
                    <p className="text-xs text-slate-400 italic">Chưa có giao dịch biến động quỹ</p>
                  )}
                </div>
              </div>
            </Card>
          </div>

          {/* CỘT PHẢI (5 cols): Bàn Ký số OTP & Quản lý Tài khoản Ký quỹ Escrow */}
          <div className="lg:col-span-5 flex flex-col gap-6">
            <Card className="p-6 border-2 border-emerald-600/30">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100 mb-4">
                <div className="flex items-center gap-2">
                  <KeyRound className="w-5 h-5 text-emerald-600" />
                  <h3 className="font-bold text-slate-900">Bàn Ký số OTP Điện tử</h3>
                </div>
                <span className="text-xs font-mono bg-slate-100 px-2 py-0.5 rounded text-slate-600">
                  Sandbox OTP
                </span>
              </div>

              {/* Hộp nhập OTP */}
              <div className="mb-4">
                <label className="block text-xs font-bold text-slate-700 mb-1">
                  Mã OTP Xác thực (Gửi qua SMS/Zalo)
                </label>
                <input
                  type="text"
                  maxLength={6}
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value)}
                  className="w-full px-3 py-2 text-center tracking-widest text-lg font-mono font-bold rounded-lg border border-slate-300 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  placeholder="123456"
                />
                <p className="text-[11px] text-slate-400 mt-1">
                  *Mã thử nghiệm mặc định: <span className="font-mono font-bold text-emerald-600">123456</span>
                </p>
              </div>

              {/* Các nút hành động tùy theo trạng thái */}
              <div className="space-y-3">
                {contract.status === 'DRAFT' && (
                  <Button
                    onClick={handleSignBuyer}
                    disabled={actionLoading}
                    className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-2.5 shadow-md flex items-center justify-center gap-2"
                  >
                    <UserCheck className="w-4 h-4" />
                    Bên Mua: Ký Số & Nạp Tiền Ký Quỹ
                  </Button>
                )}

                {contract.status === 'AWAITING_SELLER_SIGN' && (
                  <Button
                    onClick={handleSignSeller}
                    disabled={actionLoading}
                    className="w-full bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-2.5 shadow-md flex items-center justify-center gap-2"
                  >
                    <Lock className="w-4 h-4" />
                    Bên Bán: Ký Xác Nhận & Phong Tỏa Escrow
                  </Button>
                )}

                {contract.status === 'ESCROW_LOCKED' && (
                  <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl mb-2 text-center">
                    <p className="text-xs font-bold text-emerald-900">
                      🔒 TIỀN CỌC ĐANG ĐƯỢC PHONG TỎA AN TOÀN TRONG KÉT ESCROW
                    </p>
                    <p className="text-[11px] text-emerald-700 mt-1">
                      Chờ hai bên hoàn tất công chứng chuyển nhượng để giải ngân.
                    </p>
                  </div>
                )}

                {(contract.status === 'ESCROW_LOCKED' || contract.status === 'AWAITING_SELLER_SIGN') && (
                  <div className="pt-3 border-t border-slate-100 flex flex-col gap-2">
                    <Button
                      onClick={handleRelease}
                      disabled={actionLoading || contract.status !== 'ESCROW_LOCKED'}
                      className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs py-2"
                    >
                      <DollarSign className="w-3.5 h-3.5 mr-1" />
                      Giải ngân cho Bên Bán (Đã Công chứng Xong)
                    </Button>

                    <Button
                      onClick={handleRefund}
                      disabled={actionLoading}
                      variant="outline"
                      className="w-full border-rose-300 text-rose-700 hover:bg-rose-50 font-bold text-xs py-2"
                    >
                      <AlertTriangle className="w-3.5 h-3.5 mr-1" />
                      Hủy & Hoàn Tiền Cọc Cho Bên Mua (Có vi phạm)
                    </Button>
                  </div>
                )}
              </div>
            </Card>

            {/* Thẻ Két bảo đảm Escrow Vault Info */}
            <div className="p-4 rounded-xl bg-slate-900 text-white shadow-md">
              <div className="flex items-center gap-2 mb-2">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span className="text-xs font-bold uppercase tracking-wider text-emerald-400">
                  Tài Khoản Ký Quỹ Escrow Vault BDS WF
                </span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                Tài khoản ký quỹ trung gian bảo đảm không bên nào tự ý rút tiền trước khi hoàn thành công chứng. Tuân thủ chuẩn NFR12 và quy chế bảo vệ khách hàng giao dịch trực tuyến.
              </p>
              <div className="mt-3 pt-3 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
                <span>Trạng thái quỹ</span>
                <span className="text-emerald-400 font-bold">100% Khả dụng & Bảo chứng</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DepositContractPage;
