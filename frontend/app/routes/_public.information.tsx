import {
  Building2,
  ChevronRight,
  LifeBuoy,
  Scale,
  Search,
  ShieldCheck,
} from "lucide-react";
import { Link, useLocation } from "react-router-dom";

type Page = {
  title: string;
  intro: string;
  icon: typeof Building2;
  sections: { heading: string; text: string; items?: string[] }[];
};

const CONTENT: Record<string, Page> = {
  "/about": {
    title: "Về Nhà Đất Chuẩn",
    intro:
      "Nhà Đất Chuẩn là nơi người có nhu cầu mua, thuê và người đăng tin kết nối qua các tin bất động sản có thông tin rõ ràng.",
    icon: Building2,
    sections: [
      {
        heading: "Nền tảng hỗ trợ điều gì?",
        text: "Chúng tôi hỗ trợ người đăng tạo tin, người tìm nhà tra cứu thông tin và gửi yêu cầu liên hệ. Tin đăng được kiểm tra nội dung trước khi hiển thị công khai.",
        items: [
          "Tìm kiếm và xem thông tin tin đăng.",
          "Đăng tin, theo dõi trạng thái và quản lý yêu cầu liên hệ.",
          "Gửi báo cáo khi phát hiện tin có dấu hiệu sai lệch hoặc vi phạm.",
        ],
      },
      {
        heading: "Phạm vi hoạt động",
        text: "Nhà Đất Chuẩn không là bên môi giới, không đại diện cho người mua hoặc người bán, không giữ tiền và không bảo đảm việc giao dịch giữa các bên. Người dùng cần tự kiểm tra giấy tờ, hiện trạng và thỏa thuận trước khi đặt cọc hoặc thanh toán.",
      },
    ],
  },
  "/terms": {
    title: "Điều khoản sử dụng",
    intro:
      "Các điều khoản này quy định cách sử dụng nền tảng Nhà Đất Chuẩn và trách nhiệm của từng bên khi đăng hoặc tìm tin.",
    icon: Scale,
    sections: [
      {
        heading: "Tài khoản và thông tin đăng",
        text: "Người dùng chịu trách nhiệm bảo mật tài khoản và cung cấp thông tin chính xác. Người đăng phải có quyền đăng tải nội dung, hình ảnh và thông tin liên hệ trong tin của mình.",
        items: [
          "Không đăng thông tin sai sự thật, trùng lặp, gây nhầm lẫn hoặc trái pháp luật.",
          "Không sử dụng nền tảng để lừa đảo, quấy rối hoặc thu thập dữ liệu trái phép.",
          "Không đăng công khai giấy tờ định danh, số tài khoản hoặc dữ liệu cá nhân của người khác khi chưa được phép.",
        ],
      },
      {
        heading: "Kiểm duyệt và xử lý vi phạm",
        text: "Nền tảng có thể yêu cầu bổ sung thông tin, từ chối duyệt, ẩn hoặc gỡ tin vi phạm. Việc kiểm duyệt nội dung không thay thế cho trách nhiệm xác minh của các bên trước giao dịch.",
      },
      {
        heading: "Giao dịch giữa các bên",
        text: "Nhà Đất Chuẩn không thu hộ, giữ hộ, bảo lãnh hoặc giải quyết thay cho thỏa thuận dân sự giữa người dùng. Không chuyển tiền đặt cọc hay thanh toán chỉ dựa trên thông tin hiển thị trên một tin đăng.",
      },
    ],
  },
  "/privacy": {
    title: "Chính sách quyền riêng tư",
    intro:
      "Chúng tôi xử lý dữ liệu cần thiết để vận hành tài khoản, tin đăng và yêu cầu liên hệ, đồng thời giới hạn việc truy cập theo vai trò.",
    icon: ShieldCheck,
    sections: [
      {
        heading: "Dữ liệu có thể được xử lý",
        text: "Tùy chức năng bạn sử dụng, dữ liệu có thể gồm thông tin tài khoản, ảnh đại diện, số điện thoại, tin đăng, nội dung yêu cầu liên hệ và hồ sơ xác minh danh tính nếu bạn chủ động gửi.",
      },
      {
        heading: "Mục đích và phạm vi chia sẻ",
        text: "Dữ liệu được dùng để xác thực, vận hành tài khoản, hiển thị tin đăng, chuyển yêu cầu liên hệ đến đúng người đăng và bảo vệ hệ thống khỏi lạm dụng. Số liên hệ của người tìm nhà chỉ được cung cấp cho người đăng khi người đó gửi yêu cầu liên hệ.",
        items: [
          "Giấy tờ eKYC không được hiển thị công khai trên tin đăng.",
          "Chỉ nhân sự được phân quyền mới có thể truy cập hồ sơ eKYC phục vụ kiểm duyệt.",
          "Không gửi mật khẩu hoặc ảnh giấy tờ qua email hỗ trợ.",
        ],
      },
      {
        heading: "Quyền và lựa chọn của bạn",
        text: "Bạn có thể cập nhật thông tin cá nhân, quản lý tin đăng và yêu cầu liên hệ trong tài khoản. Nếu cần hỗ trợ về dữ liệu cá nhân, hãy liên hệ qua địa chỉ hỗ trợ bên dưới.",
      },
    ],
  },
  "/contact": {
    title: "Liên hệ hỗ trợ",
    intro:
      "Gửi đúng thông tin và đúng kênh để yêu cầu của bạn được tiếp nhận rõ ràng hơn.",
    icon: LifeBuoy,
    sections: [
      {
        heading: "Tư vấn và hỗ trợ tài khoản",
        text: "Bạn có thể gửi email tới nhadatchuan.online@gmail.com để được hỗ trợ về tài khoản, đăng tin, gói dịch vụ hoặc các vấn đề khi sử dụng nền tảng.",
        items: [
          "Nêu email đăng ký, đường dẫn tin đăng nếu có và mô tả ngắn vấn đề.",
          "Không gửi mật khẩu, mã xác thực hoặc ảnh CCCD/eKYC qua email.",
          "Không chuyển tiền cho bất kỳ cá nhân nào tự nhận là nhân viên hỗ trợ.",
        ],
      },
      {
        heading: "Báo cáo tin vi phạm",
        text: "Để báo cáo một tin cụ thể, hãy mở trang chi tiết của tin đó và chọn “Báo cáo tin vi phạm”. Vui lòng mô tả lý do báo cáo và thông tin giúp đối chiếu; báo cáo sẽ được chuyển đến khu vực kiểm duyệt.",
      },
    ],
  },
};

const NAV = [
  { to: "/about", label: "Giới thiệu" },
  { to: "/terms", label: "Điều khoản sử dụng" },
  { to: "/privacy", label: "Chính sách quyền riêng tư" },
  { to: "/contact", label: "Liên hệ hỗ trợ" },
];

export function InformationPage() {
  const location = useLocation();
  const page = CONTENT[location.pathname];
  if (!page) return <NotFoundPage />;
  const Icon = page.icon;
  return (
    <main className="bg-white">
      <div className="mx-auto grid max-w-6xl gap-10 px-4 py-10 md:px-8 lg:grid-cols-[220px_minmax(0,1fr)] lg:py-14">
        <aside className="lg:pt-2">
          <p className="mb-3 text-sm font-bold text-slate-900">Thông tin</p>
          <nav
            aria-label="Thông tin nền tảng"
            className="flex gap-2 overflow-x-auto pb-1 lg:flex-col"
          >
            {NAV.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className={`min-h-11 shrink-0 rounded-lg px-3 py-2 text-sm font-semibold transition ${location.pathname === item.to ? "bg-blue-50 text-blue-800" : "text-blue-800 hover:bg-blue-50 hover:text-blue-950"}`}
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </aside>
        <article className="min-w-0">
          <header className="border-b border-slate-200 pb-8">
            <div className="flex size-12 items-center justify-center rounded-xl bg-blue-50 text-blue-700">
              <Icon aria-hidden="true" className="size-6" />
            </div>
            <h1 className="mt-5 text-3xl font-bold tracking-tight text-slate-950 md:text-4xl">
              {page.title}
            </h1>
            <p className="mt-3 max-w-3xl text-base leading-7 text-slate-600 md:text-lg">
              {page.intro}
            </p>
          </header>
          <div className="divide-y divide-slate-200">
            {page.sections.map((section) => (
              <section key={section.heading} className="py-8">
                <h2 className="text-xl font-bold text-slate-950">
                  {section.heading}
                </h2>
                <p className="mt-3 leading-7 text-slate-700">{section.text}</p>
                {section.items && (
                  <ul className="mt-4 space-y-2">
                    {section.items.map((item) => (
                      <li
                        key={item}
                        className="flex gap-2 leading-6 text-slate-700"
                      >
                        <ChevronRight
                          aria-hidden="true"
                          className="mt-0.5 size-5 shrink-0 text-blue-700"
                        />
                        {item}
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            ))}
          </div>
          {location.pathname === "/contact" && (
            <section id="report" className="rounded-xl border border-blue-200 bg-blue-50 p-5">
              <div className="flex gap-3">
                <Search
                  aria-hidden="true"
                  className="mt-0.5 size-5 shrink-0 text-blue-700"
                />
                <div>
                  <h2 className="font-bold text-blue-950">
                    Chưa có đường dẫn của tin cần báo cáo?
                  </h2>
                  <p className="mt-1 leading-6 text-blue-900">
                    Tìm tin trước, sau đó mở trang chi tiết để gửi báo cáo gắn
                    với đúng tin đăng.
                  </p>
                  <Link
                    to="/search"
                    className="mt-4 inline-flex min-h-11 items-center rounded-lg bg-blue-800 px-4 text-sm font-bold text-white hover:bg-blue-900"
                  >
                    Tìm tin cần báo cáo
                  </Link>
                </div>
              </div>
            </section>
          )}
          <Link
            to="/"
            className="mt-8 inline-flex min-h-11 items-center gap-1 font-bold text-blue-800 hover:underline"
          >
            Về trang chủ <ChevronRight aria-hidden="true" className="size-4" />
          </Link>
        </article>
      </div>
    </main>
  );
}

export function NotFoundPage() {
  return (
    <main className="mx-auto max-w-xl px-4 py-20 text-center">
      <p className="text-sm font-bold text-slate-500">404</p>
      <h1 className="mt-2 text-3xl font-bold">Không tìm thấy trang</h1>
      <Link
        to="/"
        className="mt-8 inline-flex min-h-11 items-center font-bold text-blue-800 hover:underline"
      >
        Về trang chủ
      </Link>
    </main>
  );
}
