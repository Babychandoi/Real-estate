// Synthetic, local-only UI data. Never imported by app/ or the production build.
export const USER_ID = "10000000-0000-4000-8000-000000000001";
export const SELLER_ID = "20000000-0000-4000-8000-000000000001";
const date = "2026-09-27T08:00:00Z";
export const listings = Array.from({ length: 55 }, (_, index) => ({
  id: `30000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
  slug: `can-ho-minh-hoa-${index + 1}`,
  title:
    [
      "Căn hộ nhiều ánh sáng, ban công hướng công viên",
      "Nhà phố yên tĩnh, không gian cho cả gia đình",
      "Căn hộ hai phòng ngủ, gần tuyến metro",
    ][index % 3] + ` · ${index + 1}`,
  purpose: index < 30 ? "SALE" : "RENT",
  propertyType: index % 3 === 1 ? "HOUSE" : "APARTMENT",
  priceVnd:
    index < 30 ? (2.3 + (index % 8) * 0.4) * 1e9 : (7 + (index % 15)) * 1e6,
  areaM2: 65 + (index % 6) * 10,
  bedrooms: 2,
  bathrooms: 2,
  floors: 1,
  direction: "Đông Nam",
  legalStatus: "Thông tin mô phỏng",
  addressSummary: ["Cầu Giấy, Hà Nội", "Tây Hồ, Hà Nội", "Nam Từ Liêm, Hà Nội"][
    index % 3
  ],
  publicLatitude: 21.03 + (index % 6) * 0.007,
  publicLongitude: 105.79 + (index % 5) * 0.008,
  isVerified: index % 2 === 0,
  primaryImageUrl: `/__preview/image-${index % 3}.svg`,
  publishedAt: date,
  sellerId: SELLER_ID,
  sellerName: "Người đăng minh họa",
  ownerId: SELLER_ID,
  status: index % 4 === 0 ? "DRAFT" : "ACTIVE",
  revisionNumber: 1,
  revisionStatus: "APPROVED",
  imageUrls: Array.from(
    { length: 20 },
    (_, image) => `/__preview/image-${image % 3}.svg?photo=${image + 1}`,
  ),
  description:
    "Dữ liệu mô phỏng để kiểm tra giao diện. Không phải tin đăng thực tế.\nKhông gian phòng khách mở, hai phòng ngủ và ban công thoáng. Thông tin thực tế được lấy từ API khi chạy ứng dụng bình thường.",
  createdAt: date,
  updatedAt: date,
}));
export const photo = (index) =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="900" height="660" viewBox="0 0 900 660"><rect width="900" height="660" fill="${["#dce7e7", "#e6ded2", "#dce3eb"][index % 3]}"/><path d="M0 540L440 360L900 540V660H0Z" fill="#d0c2ac"/><path d="M100 100H780V490H100Z" fill="#f8f5ed"/><path d="M125 125H420V420H125Z" fill="#adc7c7"/><path d="M270 125V420M125 270H420" stroke="#f8f5ed" stroke-width="14"/><path d="M480 145H695V270H480Z" fill="#c4b595"/><path d="M425 335Q425 310 450 310H765Q790 310 790 335V475H425Z" fill="${["#55756d", "#746a61", "#546f80"][index % 3]}"/><rect x="350" y="505" width="295" height="35" rx="17" fill="#8a6a48"/><path d="M395 540V585M605 540V585" stroke="#8a6a48" stroke-width="14"/><rect x="45" y="590" width="305" height="38" rx="8" fill="#fff"/><text x="65" y="615" font-family="sans-serif" font-size="17" fill="#314354">Ảnh mô phỏng · UI preview</text></svg>`;
const paged = (items, query, size = 12) => {
  const page = Number(query.get("page") || 0);
  size = Number(query.get("size") || size);
  return {
    items: items.slice(page * size, (page + 1) * size),
    page,
    size,
    total: items.length,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
    statusCounts: { NEW: items.length },
  };
};
export function createFixtureApi(role = "ADMIN") {
  const user = {
    id: USER_ID,
    name: "Tài khoản minh họa",
    email: "preview@example.test",
    phone: "",
    role,
    planCode: "PRO",
    listingQuotaRemaining: 25,
  };
  const my = listings
    .slice(0, 12)
    .map((item) => ({ ...item, ownerId: USER_ID }));
  const kyc = {
    id: "kyc-preview",
    userId: USER_ID,
    maskedIdNumber: "********1234",
    fullName: user.name,
    status: "VERIFIED",
    createdAt: date,
    verifiedAt: date,
  };
  const leads = [
    {
      id: "lead-preview",
      listingId: listings[0].id,
      listingTitle: listings[0].title,
      listingSlug: listings[0].slug,
      listingAddress: listings[0].addressSummary,
      listingImageUrl: listings[0].primaryImageUrl,
      fullName: "Khách minh họa",
      maskedPhone: "******6789",
      requestType: "VIEWING",
      consentPolicy: true,
      status: "NEW",
      createdAt: date,
      note: "Muốn hẹn xem vào cuối tuần.",
    },
  ];
  let sla = {
    firstResponseMinutes: 30,
    reminderEnabled: true,
    dailyDigestEnabled: false,
  };
  const workspace = () => ({
    listingStats: { listings: my.length, active: 9, pending: 0 },
    leadStats: {
      leads: leads.length,
      new_leads: leads.length,
      avg_wait_minutes: 15,
    },
    sla,
    listings: my.map((item) => ({
      ...item,
      created_at: date,
      updated_at: date,
    })),
  });
  const success = (body) => ({ status: 200, body });
  return (rawUrl, method = "GET", body = {}) => {
    const url = new URL(rawUrl, "http://127.0.0.1");
    const path = url.pathname.replace(/^\/api\/v1/, "");
    const query = url.searchParams;
    if (path === "/auth/me" && method === "GET") return success(user);
    if (path === "/auth/me" && method === "PUT") {
      Object.assign(user, body);
      return success(user);
    }
    if (
      (path === "/auth/login" || path === "/auth/admin/login") &&
      method === "POST"
    )
      return success({
        accessToken: "local-ui-preview-only",
        expiresAt: "2099-01-01T00:00:00Z",
        user,
      });
    if (path === "/auth/logout" && method === "POST") return { status: 204 };
    if (path === "/notifications/stream" && method === "GET")
      return {
        status: 200,
        contentType: "text/event-stream",
        text: ": preview\n\n",
      };
    if (path === "/listings/search" && method === "GET") {
      let items = listings.filter(
        (item) =>
          (!query.get("purpose") || item.purpose === query.get("purpose")) &&
          (!query.get("propertyType") ||
            item.propertyType === query.get("propertyType")) &&
          (!query.get("keyword") ||
            `${item.title} ${item.addressSummary}`
              .toLocaleLowerCase()
              .includes(query.get("keyword").toLocaleLowerCase())),
      );
      for (const [param, field, min] of [
        ["minPrice", "priceVnd", true],
        ["maxPrice", "priceVnd", false],
        ["minArea", "areaM2", true],
        ["maxArea", "areaM2", false],
        ["minLat", "publicLatitude", true],
        ["maxLat", "publicLatitude", false],
        ["minLng", "publicLongitude", true],
        ["maxLng", "publicLongitude", false],
      ])
        if (query.has(param))
          items = items.filter((item) =>
            min
              ? item[field] >= Number(query.get(param))
              : item[field] <= Number(query.get(param)),
          );
      const sort = query.get("sortBy");
      if (sort === "PRICE_ASC") items.sort((a, b) => a.priceVnd - b.priceVnd);
      if (sort === "PRICE_DESC") items.sort((a, b) => b.priceVnd - a.priceVnd);
      if (sort === "AREA_DESC") items.sort((a, b) => b.areaM2 - a.areaM2);
      return success(paged(items, query).items);
    }
    if (path === "/listings/my-listings" && method === "GET")
      return success(my);
    if (path === "/listings/admin/all" && method === "GET")
      return success(paged(listings, query, 20).items);
    const action = path.match(/^\/listings\/([^/]+)\/(submit|visibility)$/);
    if (action && method === "POST") {
      const item = my.find((item) => item.id === action[1]);
      if (item)
        item.status =
          action[2] === "submit"
            ? "PENDING_REVIEW"
            : body.hidden
              ? "PAUSED"
              : "ACTIVE";
      return success({ success: true });
    }
    if (method === "GET" && /^\/listings\/(by-slug\/)?[^/]+$/.test(path)) {
      const item = listings.find(
        (item) =>
          item.id === path.split("/").at(-1) ||
          item.slug === decodeURIComponent(path.split("/").at(-1)),
      );
      if (item) return success(item);
    }
    if (method === "GET" && path.startsWith("/public/profiles/"))
      return success(
        path.endsWith("/listings")
          ? listings.slice(0, 6)
          : {
              displayName: "Người đăng minh họa",
              identityVerified: true,
              activeListingCount: 6,
              memberSince: date,
            },
      );
    if (path === "/public/geocoding" && method === "GET")
      return success([
        {
          display_name: "Cầu Giấy, Hà Nội, Việt Nam",
          lat: "21.035",
          lon: "105.79",
          type: "district",
          boundingbox: ["21.01", "21.08", "105.76", "105.83"],
        },
      ]);
    if (method === "GET" && path.startsWith("/kyc/user/")) return success(kyc);
    if (path === "/kyc/queue" && method === "GET") return success([]);
    if (path === "/broker/workspace" && method === "GET")
      return success(workspace());
    if (path === "/broker/workspace/sla" && method === "PUT") {
      sla = body;
      return success(workspace());
    }
    if (
      method === "GET" &&
      (path === "/leads/sent" || path === "/leads/search")
    )
      return success(paged(leads, query));
    if (path === "/leads/listings" && method === "GET")
      return success(
        paged(
          [
            {
              listingId: listings[0].id,
              title: listings[0].title,
              slug: listings[0].slug,
              imageUrl: listings[0].primaryImageUrl,
              address: listings[0].addressSummary,
              totalLeads: 1,
              newLeads: 1,
              activeLeads: 1,
              closedLeads: 0,
              lastLeadAt: date,
            },
          ],
          query,
        ),
      );
    if (path === "/leads" && method === "GET") return success(leads);
    if (path === "/public/leads" && method === "POST")
      return success({
        id: "lead-created-preview",
        listingId: body.listingId,
        status: "NEW",
      });
    if (path === "/public/reports" && method === "POST")
      return success({ id: "report-created-preview", status: "PENDING" });
    if (path === "/analytics/funnel" && method === "GET")
      return success({
        impressions: 0,
        detailViews: 0,
        leadsSubmitted: 12,
        contactedCount: 8,
        dealsClosed: 3,
        conversionRatePercent: 25,
        steps: [
          {
            stepIndex: 1,
            stepName: "Yêu cầu đã gửi",
            count: 12,
            percentage: 100,
          },
          { stepIndex: 2, stepName: "Đã liên hệ", count: 8, percentage: 66.7 },
          { stepIndex: 3, stepName: "Đã chốt", count: 3, percentage: 37.5 },
        ],
      });
    if (path === "/admin/users" && method === "GET")
      return success(
        paged(
          [
            {
              ...user,
              fullName: user.name,
              status: "ACTIVE",
              kycStatus: "VERIFIED",
              listingCount: 12,
              createdAt: date,
              emailVerifiedAt: date,
              lastLoginAt: date,
              planExpiresAt: null,
            },
          ],
          query,
          20,
        ),
      );
    if (path === "/billing/plans" && method === "GET")
      return success([
        {
          code: "FREE",
          name: "Khởi đầu",
          priceVnd: 0,
          quota: 3,
          durationDays: 30,
          description: "Gói mô phỏng",
        },
        {
          code: "STANDARD",
          name: "Tiêu chuẩn",
          priceVnd: 299000,
          quota: 15,
          durationDays: 30,
          description: "Gói mô phỏng",
        },
        {
          code: "PRO",
          name: "Chuyên nghiệp",
          priceVnd: 599000,
          quota: 40,
          durationDays: 30,
          description: "Gói mô phỏng",
        },
      ]);
    if (path === "/billing/admin/orders" && method === "GET")
      return success(paged([], query, 20));
    if (path === "/billing/admin/bank" && method === "GET")
      return { status: 204 };
    if (
      [
        "/billing/orders",
        "/billing/admin/reconciliation",
        "/moderation/queue",
        "/moderation/rejection-reasons",
        "/reports",
        "/verifications",
        "/catalog/projects",
        "/cms/articles",
      ].includes(path) &&
      method === "GET"
    )
      return success([]);
    return {
      status: method === "GET" ? 404 : 405,
      body: {
        title: "UI preview",
        status: method === "GET" ? 404 : 405,
        detail:
          "Thao tác này chưa được mô phỏng. Chạy với backend thật để kiểm tra nghiệp vụ.",
      },
    };
  };
}
