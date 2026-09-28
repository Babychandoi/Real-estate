import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import {
  createFixtureApi,
  listings,
  USER_ID,
  SELLER_ID,
  photo,
} from "./fixtures.mjs";
const admin = "/2026/nhadatchuan/admin";
const routes: Array<[string, string]> = [
  ["/", "GUEST"],
  ["/search", "GUEST"],
  ["/search?purpose=RENT", "GUEST"],
  [`/listings/${listings[0].slug}`, "GUEST"],
  ["/compare", "GUEST"],
  [`/nguoi-dang/${SELLER_ID}`, "GUEST"],
  ["/about", "GUEST"],
  ["/terms", "GUEST"],
  ["/privacy", "GUEST"],
  ["/contact", "GUEST"],
  ["/forgot-password", "GUEST"],
  ["/reset-password", "GUEST"],
  ["/verify-email", "GUEST"],
  ["/missing-page", "GUEST"],
  ["/account", "BROKER"],
  ["/my-listings", "BROKER"],
  ["/my-leads", "BROKER"],
  ["/broker/workspace", "BROKER"],
  ["/billing", "BROKER"],
  ["/kyc", "USER"],
  ["/my-inquiries", "USER"],
  ["/listings/new", "BROKER"],
  [`/listings/new?edit=${listings[1].id}`, "BROKER"],
  [admin, "ADMIN"],
  ...[
    "moderation",
    "listings",
    "users",
    "leads-and-reports",
    "verification",
    "billing",
    "analytics",
    "projects",
    "cms",
  ].map((route) => [`${admin}/${route}`, "ADMIN"] as [string, string]),
  [`${admin}/login`, "GUEST"],
];
async function fixture(page: Page, role = "GUEST") {
  // Keep local UI regressions independent of the external font CDN.
  await page.route("https://fonts.googleapis.com/**", (route) =>
    route.fulfill({ contentType: "text/css", body: "" }),
  );
  const api = createFixtureApi(role === "GUEST" ? "USER" : role);
  const unknown: string[] = [];
  if (role !== "GUEST")
    await page.addInitScript(() =>
      sessionStorage.setItem("bds_access_token", "ui-test-only"),
    );
  await page.route("**/__preview/image-*", (route) =>
    route.fulfill({ contentType: "image/svg+xml", body: photo(1) }),
  );
  await page.route("**/api/v1/**", (route) => {
    const req = route.request();
    let body = {};
    try {
      body = req.postDataJSON() || {};
    } catch {
      /* form */
    }
    const result = api(req.url(), req.method(), body);
    if (result.status === 404) unknown.push(new URL(req.url()).pathname);
    return route.fulfill({
      status: result.status,
      contentType: result.contentType || "application/json",
      body:
        result.status === 204 ? "" : result.text || JSON.stringify(result.body),
    });
  });
  return unknown;
}
for (const [url, role] of routes) {
  test(`route ${url} (${role})`, async ({ page }, testInfo) => {
    const unknown = await fixture(page, role);
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto(url);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expect(page.getByRole("main")).toHaveCount(1);
    await expect(
      page.getByRole("heading", { name: "Chưa thể tải nội dung" }),
    ).toHaveCount(0);
    await expect
      .poll(() =>
        page.evaluate(
          () => document.documentElement.scrollWidth - window.innerWidth,
        ),
      )
      .toBeLessThanOrEqual(1);
    expect(unknown).toEqual([]);
    expect(errors).toEqual([]);
    if (
      ["ui-360", "ui-1440"].includes(testInfo.project.name) &&
      [
        "/",
        "/search",
        "/my-listings",
        `${admin}/users`,
        `/listings/${listings[0].slug}`,
      ].includes(url)
    )
      await page.screenshot({
        path: `test-results/screenshots/${testInfo.project.name}-${url.replace(/[^a-z0-9]/gi, "_") || "home"}.png`,
        fullPage: true,
      });
  });
}
test("search paginates and restores filters after reload and back", async ({
  page,
}) => {
  await fixture(page);
  await page.goto("/search");
  await expect(page.getByRole("article")).toHaveCount(24);
  await page.getByRole("button", { name: "Trang tiếp", exact: true }).click();
  await expect(page).toHaveURL(/page=2/);
  await expect(page.getByRole("article")).toHaveCount(6);
  await page.reload();
  await expect(page.getByRole("article")).toHaveCount(6);
  await page.getByRole("button", { name: "Thuê nhà", exact: true }).click();
  await page.getByLabel("Giá thuê mỗi tháng").selectOption("LOW");
  await expect(page).toHaveURL(/priceRange=LOW/);
  await expect(page).not.toHaveURL(/page=2/);
  await expect(page.getByRole("article").first()).toContainText("/tháng");
  const request = page.waitForRequest(
    (request) =>
      request.url().includes("/listings/search") &&
      request.url().includes("minPrice=10000000") &&
      request.url().includes("maxPrice=20000000"),
  );
  await page.getByLabel("Giá thuê mỗi tháng").selectOption("MID");
  await request;
  await page.reload();
  await expect(page.getByLabel("Giá thuê mỗi tháng")).toHaveValue("MID");
  await page.goBack();
  await expect(page.getByLabel("Giá thuê mỗi tháng")).toHaveValue("LOW");
});
test("search handles invalid URL and retry/empty states", async ({ page }) => {
  await fixture(page);
  await page.goto("/search?purpose=invalid&page=-20&sortBy=invalid");
  await expect(page.getByRole("article")).toHaveCount(24);
  await expect(page.getByLabel("Sắp xếp")).toHaveValue("LATEST");
  await page.route("**/api/v1/listings/search**", (route) =>
    route.fulfill({
      status: 503,
      json: {
        title: "Unavailable",
        detail: "Fixture unavailable",
        status: 503,
      },
    }),
  );
  await page.getByLabel("Sắp xếp").selectOption("PRICE_ASC");
  await expect(page.getByRole("alert")).toBeVisible();
  await page.unroute("**/api/v1/listings/search**");
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(page.getByRole("article")).toHaveCount(24);
  await page
    .getByRole("combobox", { name: "Từ khóa hoặc địa điểm" })
    .fill("no-result-xyz");
  await page.getByRole("button", { name: "Tìm kiếm", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Chưa tìm thấy tin phù hợp" }),
  ).toBeVisible();
});
test("gallery exposes all 20 images with keyboard and restores focus", async ({
  page,
}) => {
  await fixture(page);
  await page.goto(`/listings/${listings[0].slug}`);
  const trigger = page.getByRole("button", { name: "Xem 20 ảnh" });
  await trigger.click();
  const dialog = page.getByRole("dialog", { name: "Ảnh bất động sản" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: "Xem ảnh 20", exact: true }).click();
  await expect(dialog.getByText("Ảnh 20 / 20")).toBeVisible();
  await page.keyboard.press("ArrowRight");
  await expect(dialog.getByText("Ảnh 1 / 20")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(dialog).not.toBeVisible();
  await expect(trigger).toBeFocused();
});
test("card compare is independent of navigation and persists", async ({
  page,
}) => {
  await fixture(page);
  await page.goto("/search");
  await page
    .getByRole("button", {
      name: `Thêm ${listings[0].title} vào danh sách so sánh`,
      exact: true,
    })
    .click();
  await page
    .getByRole("button", {
      name: `Thêm ${listings[1].title} vào danh sách so sánh`,
      exact: true,
    })
    .click();
  await expect(page).toHaveURL(/\/search/);
  await page.goto("/compare");
  await expect(page.getByRole("main")).toContainText(listings[0].title);
  await expect(page.getByRole("main")).toContainText(listings[1].title);
  await page.reload();
  await expect(page.getByRole("main")).toContainText(listings[1].title);
});
test("mobile menu has keyboard containment and role links", async ({
  page,
}) => {
  await fixture(page, "BROKER");
  await page.setViewportSize({ width: 360, height: 800 });
  await page.goto("/");
  const trigger = page.getByRole("button", { name: "Mở menu", exact: true });
  await trigger.click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(
    dialog.getByRole("link", { name: "Tin đăng của tôi" }),
  ).toBeVisible();
  for (let i = 0; i < 20; i++) {
    await page.keyboard.press("Tab");
    expect(
      await page.evaluate(
        () => document.activeElement?.closest("dialog") !== null,
      ),
    ).toBeTruthy();
  }
  await page.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
});
test("my listings confirms mutation and reports failed save", async ({
  page,
}) => {
  await fixture(page, "BROKER");
  await page.goto("/my-listings");
  await page.getByRole("button", { name: "Bản nháp", exact: true }).click();
  await page
    .getByRole("button", { name: "Nộp duyệt", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("status").filter({ hasText: "Tin đã được gửi duyệt." }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Đang hiển thị", exact: true })
    .click();
  await page.route("**/api/v1/listings/*/visibility", (route) =>
    route.fulfill({
      status: 500,
      json: { title: "Failure", detail: "Failure", status: 500 },
    }),
  );
  await page
    .getByRole("button", { name: "Ẩn tin", exact: true })
    .first()
    .click();
  await expect(page.getByRole("alert")).toContainText("Thao tác chưa được lưu");
});
test("protected admin page does not expose role controls to USER", async ({
  page,
}) => {
  await fixture(page, "USER");
  await page.goto(`${admin}/users`);
  await expect(page).toHaveURL(/\/my-inquiries$/);
  await expect(
    page.getByRole("button", { name: /Khóa tài khoản/ }),
  ).toHaveCount(0);
});
test("key screens have no serious WCAG A/AA violations", async ({
  page,
}, testInfo) => {
  test.setTimeout(120_000);
  if (testInfo.project.name !== "ui-1440") {
    await fixture(page);
    await page.goto("/search");
  } else {
    await fixture(page, "ADMIN");
  }
  const checks =
    testInfo.project.name === "ui-1440"
      ? [
          "/",
          "/search",
          `/listings/${listings[0].slug}`,
          "/compare",
          "/account",
          "/my-listings",
          `${admin}/users`,
          `${admin}/moderation`,
          `${admin}/billing`,
          `${admin}/cms`,
        ]
      : ["/search"];
  for (const url of checks) {
    await page.goto(url);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    const result = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(
      result.violations
        .filter(
          (item) => item.impact === "critical" || item.impact === "serious",
        )
        .map((item) => ({
          id: item.id,
          nodes: item.nodes.map((node) => node.html),
        })),
      url,
    ).toEqual([]);
  }
});

test("map is lazy and preserves area URL without refetch loop", async ({
  page,
}) => {
  await fixture(page);
  const maps: string[] = [];
  const searches: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("maplibre")) maps.push(request.url());
    if (request.url().includes("/listings/search"))
      searches.push(request.url());
  });
  await page.route("https://tiles.openfreemap.org/**", (route) =>
    route.abort(),
  );
  await page.goto("/search");
  await expect(page.getByRole("article")).toHaveCount(24);
  expect(maps).toEqual([]);
  await page
    .getByRole("combobox", { name: "Từ khóa hoặc địa điểm" })
    .fill("Cầu Giấy");
  await page
    .getByRole("button", { name: "Cầu Giấy, Hà Nội", exact: true })
    .click();
  await expect(page).toHaveURL(/minLat=21.010000/);
  await expect(page).toHaveURL(/view=map/);
  await expect(
    page.getByRole("region", { name: "Bản đồ kết quả" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Thử tải bản đồ lại" }),
  ).toBeVisible();
  expect(searches.filter((url) => url.includes("minLat=21.01")).length).toBe(1);
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Bỏ giới hạn khu vực" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Danh sách", exact: true }).click();
  await expect(
    page.getByRole("region", { name: "Bản đồ kết quả" }),
  ).toHaveCount(0);
});
test("login opens a real form and updates the account navigation", async ({
  page,
}) => {
  await fixture(page);
  await page.goto("/");
  if (
    await page.getByRole("button", { name: "Mở menu", exact: true }).isVisible()
  ) {
    await page.getByRole("button", { name: "Mở menu", exact: true }).click();
    await page.getByRole("button", { name: "Đăng nhập / Đăng ký" }).click();
  } else
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await page.getByLabel("Email", { exact: true }).fill("preview@example.test");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("PreviewPassword123");
  await page
    .getByRole("button", { name: "Đăng nhập", exact: true })
    .last()
    .click();
  await expect(
    page.getByRole("dialog", { name: "Đăng nhập", exact: true }),
  ).not.toBeVisible();
  await page.goto("/account");
  await expect(
    page.getByRole("heading", { name: "Thông tin cá nhân" }),
  ).toBeVisible();
});
test("CMS load failure is visible and retry recovers", async ({ page }) => {
  await fixture(page, "ADMIN");
  await page.route("**/api/v1/cms/articles", (route) =>
    route.fulfill({ status: 503, json: { title: "Unavailable", status: 503 } }),
  );
  await page.goto(`${admin}/cms`);
  await expect(page.getByRole("alert")).toContainText(
    "Không thể tải nội dung CMS",
  );
  await page.unroute("**/api/v1/cms/articles");
  await page.getByRole("button", { name: "Thử lại", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("listing metadata is restored when leaving the detail page", async ({
  page,
}) => {
  await fixture(page);
  await page.goto("/search");
  const originalTitle = await page.title();
  const originalCanonical = await page
    .locator('link[rel="canonical"]')
    .evaluateAll((nodes) => nodes[0]?.getAttribute("href") ?? null);
  await page
    .getByRole("link", {
      name: `Xem chi tiết: ${listings[0].title}`,
      exact: true,
    })
    .click();
  await expect(page).toHaveTitle(`${listings[0].title} | Nhà Đất Chuẩn`);
  await expect(page.locator("head script#listing-structured-data")).toHaveCount(
    1,
  );
  await page.goBack();
  await expect(page.getByRole("article")).toHaveCount(24);
  await expect(page).toHaveTitle(originalTitle);
  await expect(page.locator("head script#listing-structured-data")).toHaveCount(
    0,
  );
  const canonical = await page
    .locator('link[rel="canonical"]')
    .evaluateAll((nodes) => nodes[0]?.getAttribute("href") ?? null);
  expect(canonical).toBe(originalCanonical);
});
