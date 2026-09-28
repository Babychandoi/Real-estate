import { useEffect, useState } from "react";
import { Check, Layers, ShieldCheck, Zap } from "lucide-react";
import { apiClient } from "@/shared/api/client";
import { useAuth } from "@/shared/auth/AuthContext";

type Plan = {
  code: string;
  name: string;
  priceVnd: number;
  quota: number;
  durationDays: number;
  description: string;
};
type Order = {
  id: string;
  userId: string;
  planCode: string;
  amountVnd: number;
  reference: string;
  status: string;
  createdAt: string;
  qrUrl: string;
};
type Bank = {
  bankBin: string;
  bankName: string;
  accountNumber: string;
  accountName: string;
  adminEmail: string;
  version: number;
};
const ORDER_STATUS: Record<string, string> = {
  CREATED: "Chờ chuyển khoản",
  TRANSFER_REPORTED: "Đang chờ đối soát",
  APPROVED: "Đã nâng cấp",
  REJECTED: "Đối soát bị từ chối",
  CANCELLED: "Đã hủy",
};

const highlightsFor = (plan: Plan, plans: Plan[]) => {
  const standard = plans.find((item) => item.code === "STANDARD");
  const free = plans.find((item) => item.code === "FREE");
  if (plan.code === "FREE") {
    return [
      `${plan.quota} lượt đăng để trải nghiệm nền tảng`,
      `Dùng trong ${plan.durationDays} ngày`,
      "Không cần thanh toán để bắt đầu",
    ];
  }
  const perListing = Math.round(plan.priceVnd / plan.quota);
  if (plan.code === "PRO" && standard) {
    const standardUnitPrice = standard.priceVnd / standard.quota;
    const savings = Math.round((1 - perListing / standardUnitPrice) * 100);
    return [
      `${plan.quota} lượt đăng, khoảng ${perListing.toLocaleString("vi-VN")}đ/lượt`,
      standard.quota > 0
        ? `${Math.floor(plan.quota / standard.quota)} lần số lượt của gói ${standard.name}`
        : `${plan.quota} lượt đăng trong một đơn`,
      savings > 0
        ? `Tiết kiệm khoảng ${savings}% mỗi lượt so với gói ${standard.name}`
        : `Hiệu lực ${plan.durationDays} ngày`,
      `Lượt được cộng sau khi đối soát thanh toán`,
    ];
  }
  if (plan.code === "STANDARD") {
    return [
      `${plan.quota} lượt đăng, khoảng ${perListing.toLocaleString("vi-VN")}đ/lượt`,
      free && free.quota > 0
        ? `${Math.floor(plan.quota / free.quota)} lần số lượt của gói ${free.name}`
        : `Dùng trong ${plan.durationDays} ngày`,
      `Lượt được cộng sau khi đối soát thanh toán`,
    ];
  }
  return [
    `${plan.quota} lượt đăng được cộng sau khi thanh toán được xác nhận`,
    `Thời hạn ${plan.durationDays} ngày`,
    `Khoảng ${perListing.toLocaleString("vi-VN")}đ cho mỗi lượt`,
  ];
};

export function BillingPage() {
  const { user } = useAuth();
  const [plans, setPlans] = useState<Plan[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [queue, setQueue] = useState<Order[]>([]);
  const [bank, setBank] = useState<Bank>({
    bankBin: "",
    bankName: "",
    accountNumber: "",
    accountName: "",
    adminEmail: "",
    version: 0,
  });
  const [rejectReasons, setRejectReasons] = useState<Record<string, string>>(
    {},
  );
  const [msg, setMsg] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const run = async (action: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    setError("");
    setMsg("");
    try {
      await action();
    } catch (e) {
      setError(
        e instanceof Error
          ? e.message
          : "Không thể thực hiện thao tác. Vui lòng thử lại.",
      );
    } finally {
      setBusy(false);
    }
  };
  const load = () => {
    apiClient<Plan[]>("/billing/plans")
      .then(setPlans)
      .catch((e) => setError(e.message));
    apiClient<Order[]>("/billing/orders")
      .then(setOrders)
      .catch((e) => setError(e.message));
    if (user?.role === "ADMIN") {
      apiClient<Order[]>("/billing/admin/reconciliation")
        .then(setQueue)
        .catch((e) => setError(e.message));
      apiClient<Bank>("/billing/admin/bank")
        .then((value) => value && setBank(value))
        .catch((e) => setError(e.message));
    }
  };
  useEffect(load, [user?.role]);
  const buy = async (code: string) => {
    await apiClient("/billing/orders", {
      method: "POST",
      body: JSON.stringify({ planCode: code }),
    });
    setMsg("Đã tạo mã thanh toán. Quét QR và ghi đúng nội dung chuyển khoản.");
    load();
  };
  const report = async (id: string) => {
    await apiClient(`/billing/orders/${id}/reported`, { method: "POST" });
    setMsg("Đã báo chuyển khoản. Quản trị viên sẽ đối soát thủ công.");
    load();
  };
  const cancel = async (id: string) => {
    await apiClient(`/billing/orders/${id}/cancel`, { method: "POST" });
    setMsg("Đã hủy yêu cầu thanh toán.");
    load();
  };
  const approve = async (id: string) => {
    await apiClient(`/billing/admin/reconciliation/${id}/approve`, {
      method: "POST",
      body: JSON.stringify({ note: "Đã đối chiếu thủ công" }),
    });
    setMsg("Đã xác nhận và cộng lượt đăng cho tài khoản.");
    load();
  };
  const reject = async (id: string) => {
    await apiClient(`/billing/admin/reconciliation/${id}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason: rejectReasons[id] }),
    });
    setMsg("Đã từ chối và thông báo lý do cho người dùng.");
    load();
  };
  const saveBank = async () => {
    await apiClient("/billing/admin/bank", {
      method: "PUT",
      body: JSON.stringify(bank),
    });
    setMsg("Đã lưu tài khoản nhận tiền.");
    load();
  };
  const currentPlan = plans.find((plan) => plan.code === user?.planCode);

  return (
    <section className="mx-auto max-w-6xl px-4 py-10 md:px-8">
      <header className="border-b border-slate-200 pb-8">
        <div className="flex flex-col justify-between gap-5 md:flex-row md:items-end">
          <div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-950 md:text-4xl">
              Nâng cấp lượt đăng tin
            </h1>
            <p className="mt-3 max-w-2xl leading-7 text-slate-600">
              Chọn số lượt phù hợp với nhu cầu đăng tin. Mỗi đơn được thanh toán
              một lần; lượt đăng sẽ được cộng sau khi quản trị viên xác nhận
              chuyển khoản.
            </p>
          </div>
          <div className="rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-950">
            <p className="font-bold">
              Gói hiện tại: {currentPlan?.name || "Miễn phí"}
            </p>
            <p className="mt-1">
              Còn{" "}
              <strong className="tabular-nums">
                {user?.listingQuotaRemaining ?? 0}
              </strong>{" "}
              lượt đăng
            </p>
          </div>
        </div>
      </header>
      {error && (
        <div
          role="alert"
          className="mt-6 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm font-medium text-rose-800"
        >
          {error}
        </div>
      )}
      {msg && (
        <div
          role="status"
          className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-medium text-emerald-900"
        >
          {msg}
        </div>
      )}
      <section
        className="mt-8 grid gap-5 md:grid-cols-3"
        aria-label="Các gói đăng tin"
      >
        {plans.map((plan) => {
          const featured = plan.code === "PRO";
          const perListing =
            plan.priceVnd > 0 ? Math.round(plan.priceVnd / plan.quota) : 0;
          const planButtonTone =
            plan.priceVnd === 0
              ? "bg-slate-200 text-slate-700"
              : featured
                ? "bg-emerald-700 text-white hover:bg-emerald-800"
                : "bg-slate-950 text-white hover:bg-slate-800";
          return (
            <article
              key={plan.code}
              className={`relative rounded-xl border p-6 ${featured ? "border-emerald-500 bg-emerald-50/40" : "border-slate-200 bg-white"}`}
            >
              {featured && (
                <span className="absolute -top-3 left-6 rounded-full bg-emerald-700 px-3 py-1 text-xs font-bold text-white">
                  Hiệu quả cho đăng nhiều tin
                </span>
              )}
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="text-xl font-bold text-slate-950">
                    {plan.name}
                  </h2>
                  <p className="mt-2 text-3xl font-bold tabular-nums text-slate-950">
                    {plan.priceVnd.toLocaleString("vi-VN")}đ
                  </p>
                  <p className="mt-1 text-sm text-slate-600">
                    {plan.quota} lượt đăng · {plan.durationDays} ngày
                  </p>
                </div>
                {featured ? (
                  <Zap className="h-6 w-6 text-emerald-700" />
                ) : (
                  <Layers className="h-6 w-6 text-blue-700" />
                )}
              </div>
              <div className="my-5 rounded-lg bg-slate-50 px-4 py-3">
                <p className="text-xs font-bold uppercase tracking-wide text-slate-600">
                  Chi phí theo lượt
                </p>
                <p className="mt-1 font-bold text-slate-950">
                  {plan.priceVnd === 0
                    ? "0đ để bắt đầu"
                    : `Chỉ khoảng ${perListing.toLocaleString("vi-VN")}đ / lượt đăng`}
                </p>
              </div>
              <ul className="space-y-3">
                {highlightsFor(plan, plans).map((benefit) => (
                  <li
                    key={benefit}
                    className="flex gap-2 text-sm leading-6 text-slate-700"
                  >
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" />
                    {benefit}
                  </li>
                ))}
              </ul>
              <button
                disabled={busy || plan.priceVnd === 0}
                onClick={() => void run(() => buy(plan.code))}
                className={`mt-6 min-h-11 w-full rounded-lg px-4 text-sm font-bold ${planButtonTone} disabled:cursor-not-allowed disabled:opacity-60`}
              >
                {plan.priceVnd === 0 ? "Gói mặc định" : `Chọn gói ${plan.name}`}
              </button>
            </article>
          );
        })}
      </section>
      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-5 md:p-6">
        <h2 className="text-xl font-bold text-slate-950">
          Các công cụ bạn dùng cùng lượt đăng
        </h2>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
          Khi tin được tạo, bạn quản lý toàn bộ quá trình ngay trên tài khoản
          môi giới.
        </p>
        <div className="mt-5 grid gap-5 md:grid-cols-3 md:divide-x md:divide-slate-200">
          <div className="md:pr-5">
            <p className="font-bold text-slate-950">Kho tin của bạn</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Theo dõi tin đang soạn, chờ duyệt hoặc hiển thị; quản lý trạng
              thái tin tại một nơi.
            </p>
          </div>
          <div className="md:px-5">
            <p className="font-bold text-slate-950">Hộp thư theo từng bài</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Xem yêu cầu liên hệ, nội dung khách để lại và cập nhật tiến độ
              chăm sóc.
            </p>
          </div>
          <div className="md:pl-5">
            <p className="font-bold text-slate-950">Không gian môi giới</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Theo dõi số tin, lead mới và thời gian chờ phản hồi trên dữ liệu
              tài khoản.
            </p>
          </div>
        </div>
      </section>
      <section className="mt-8 rounded-xl border border-blue-200 bg-blue-50 p-5">
        <div className="flex gap-3">
          <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-blue-800" />
          <div>
            <h2 className="font-bold text-blue-950">
              Quy trình thanh toán rõ ràng
            </h2>
            <ol className="mt-2 space-y-1 text-sm leading-6 text-blue-900">
              <li>1. Chọn gói để tạo mã thanh toán VietQR.</li>
              <li>2. Chuyển đúng số tiền và nội dung hiển thị trong đơn.</li>
              <li>
                3. Bấm “Tôi đã chuyển khoản”; quản trị viên kiểm tra và cộng
                lượt sau khi xác nhận.
              </li>
            </ol>
          </div>
        </div>
      </section>
      <section className="mt-10">
        <h2 className="text-xl font-bold text-slate-950">Thanh toán của tôi</h2>
        <p className="mt-1 text-sm text-slate-600">
          Theo dõi từng yêu cầu thanh toán và trạng thái đối soát.
        </p>
        <div className="mt-4 grid gap-4">
          {orders.length === 0 ? (
            <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-600">
              Chưa có yêu cầu thanh toán nào.
            </div>
          ) : (
            orders.map((order) => (
              <article
                key={order.id}
                className="flex flex-col items-center gap-5 rounded-xl border border-slate-200 bg-white p-5 md:flex-row"
              >
                {order.qrUrl && (
                  <img
                    src={order.qrUrl}
                    alt={`QR chuyển khoản ${order.reference}`}
                    className="h-40 w-40 object-contain"
                  />
                )}
                <div className="min-w-0 flex-1">
                  <p className="font-bold text-slate-950">
                    {plans.find((plan) => plan.code === order.planCode)?.name ||
                      "Gói đăng tin"}{" "}
                    · {order.amountVnd.toLocaleString("vi-VN")}đ
                  </p>
                  <p className="mt-2 text-sm text-slate-700">
                    Nội dung bắt buộc:{" "}
                    <strong className="text-emerald-800">
                      {order.reference}
                    </strong>
                  </p>
                  <p className="mt-1 text-sm text-slate-600">
                    Trạng thái: {ORDER_STATUS[order.status] || "Chưa xác định"}
                  </p>
                </div>
                {order.status === "CREATED" && (
                  <div className="flex flex-wrap gap-2">
                    <button
                      disabled={busy}
                      onClick={() => void run(() => report(order.id))}
                      className="min-h-11 rounded-lg bg-slate-950 px-4 text-sm font-bold text-white"
                    >
                      Tôi đã chuyển khoản
                    </button>
                    <button
                      disabled={busy}
                      onClick={() => void run(() => cancel(order.id))}
                      className="min-h-11 rounded-lg border border-slate-300 px-4 text-sm font-bold text-slate-800"
                    >
                      Hủy yêu cầu
                    </button>
                  </div>
                )}
              </article>
            ))
          )}
        </div>
      </section>
      {user?.role === "ADMIN" && (
        <>
          <section className="mt-10 rounded-xl border border-slate-200 bg-white p-6">
            <h2 className="text-xl font-bold">Tài khoản nhận VietQR</h2>
            <div className="mt-4 grid gap-3 md:grid-cols-2">
              {(
                [
                  "bankBin",
                  "bankName",
                  "accountNumber",
                  "accountName",
                  "adminEmail",
                ] as const
              ).map((key) => (
                <label key={key} className="text-sm font-semibold">
                  {
                    {
                      bankBin: "Mã BIN ngân hàng (6 số)",
                      bankName: "Tên ngân hàng",
                      accountNumber: "Số tài khoản",
                      accountName: "Tên chủ tài khoản",
                      adminEmail: "Email nhận thông báo",
                    }[key]
                  }
                  <input
                    value={bank[key] || ""}
                    onChange={(event) =>
                      setBank({ ...bank, [key]: event.target.value })
                    }
                    className="mt-1 w-full rounded-lg border border-slate-300 p-3 text-sm"
                  />
                </label>
              ))}
            </div>
            <button
              disabled={busy}
              onClick={() => void run(saveBank)}
              className="mt-4 min-h-11 rounded-lg bg-emerald-700 px-5 text-sm font-bold text-white"
            >
              Lưu cấu hình
            </button>
          </section>
          <section className="mt-8">
            <h2 className="text-xl font-bold">Chờ đối soát ({queue.length})</h2>
            <div className="mt-3 grid gap-3">
              {queue.map((order) => (
                <div
                  key={order.id}
                  className="grid gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 md:grid-cols-[1fr_1fr_auto]"
                >
                  <span className="font-semibold">
                    {order.reference} ·{" "}
                    {order.amountVnd.toLocaleString("vi-VN")}đ
                  </span>
                  <input
                    aria-label={`Lý do từ chối ${order.reference}`}
                    value={rejectReasons[order.id] || ""}
                    onChange={(event) =>
                      setRejectReasons({
                        ...rejectReasons,
                        [order.id]: event.target.value,
                      })
                    }
                    placeholder="Lý do nếu từ chối"
                    className="min-h-11 rounded-lg border border-amber-200 bg-white px-3 text-sm"
                  />
                  <div className="flex gap-2">
                    <button
                      disabled={busy}
                      onClick={() => void run(() => approve(order.id))}
                      className="min-h-11 rounded-lg bg-emerald-700 px-4 text-sm font-bold text-white"
                    >
                      Xác nhận
                    </button>
                    <button
                      disabled={busy || !rejectReasons[order.id]?.trim()}
                      onClick={() => void run(() => reject(order.id))}
                      className="min-h-11 rounded-lg bg-rose-700 px-4 text-sm font-bold text-white disabled:opacity-50"
                    >
                      Từ chối
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </>
      )}
    </section>
  );
}

export default BillingPage;
