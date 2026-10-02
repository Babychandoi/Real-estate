package com.company.bds.analytics.application;

import com.company.bds.analytics.application.port.out.AnalyticsDashboardQueries;
import com.company.bds.analytics.application.port.out.AnalyticsDashboardQueries.Filters;
import com.company.bds.analytics.application.port.out.AnalyticsDashboardQueries.Window;
import com.company.bds.analytics.domain.Metric;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Admin analytics dashboard (F19.3, P-10, P-13, F17.4, F15.3, UI-24). Every number carries its status, definition and
 * data source, so a pipeline that is off or a filter that does not apply is shown as "chưa đo", never as 0. Bot and
 * internal traffic are excluded everywhere. Web metrics count consented visitors only (nothing else is stored).
 */
@Service
public class AnalyticsDashboardService {
    public static final int MAX_DAYS = 92;
    public static final int DEFAULT_DAYS = 28;
    private static final Set<String> DEVICES = Set.of("mobile", "tablet", "desktop");
    private static final Pattern AREA = Pattern.compile("[0-9]{1,5}");
    private static final Pattern SOURCE = Pattern.compile("[a-z0-9._-]{1,100}|\\(direct\\)");
    private static final String WEB = "web";
    private static final String DATABASE = "database";
    private static final String SERVER = "server";
    private static final String AGGREGATE = "aggregate";

    public record DashboardRequest(@Nullable LocalDate from, @Nullable LocalDate to, @Nullable String device,
                                   @Nullable String area, @Nullable String source) {}

    public record WindowView(LocalDate from, LocalDate to, int days, String timezone) {}

    public record Collection(boolean ingestionEnabled, long webEvents, long webSessions, String scope) {}

    public record Freshness(@Nullable Instant latestWebEventAt, @Nullable Instant aggregatesComputedAt, String rawLatency,
                            String aggregateLatency) {}

    public record FunnelStep(String key, String label, Metric count, @Nullable Metric fromPrevious) {}

    public record Funnel(String key, String title, String definition, String source, List<FunnelStep> steps) {}

    public record NamedMetric(String key, String label, String group, String definition, String source, Metric metric) {}

    public record CohortRow(LocalDate week, long devices, List<Double> retentionPercent) {}

    public record Cohorts(Metric.Status status, @Nullable String reason, String definition, List<CohortRow> rows) {}

    public record VitalRow(String metric, String device, Metric p75, long samples, double goodAtMost, double poorAbove,
                           String rating) {}

    public record Alert(String severity, String code, String message) {}

    public record Dashboard(WindowView window, Filters filters, Instant generatedAt, Collection collection, Freshness freshness,
                            List<Funnel> funnels, List<NamedMetric> metrics, Cohorts cohorts, List<VitalRow> webVitals,
                            @Nullable String webVitalsReason, Map<String, List<AnalyticsDashboardQueries.Breakdown>> breakdowns,
                            List<AnalyticsDashboardQueries.TrendDay> trend, List<Alert> alerts) {}

    private final AnalyticsDashboardQueries queries;
    private final Clock clock;
    private final boolean ingestionEnabled;

    public AnalyticsDashboardService(AnalyticsDashboardQueries queries, Clock clock,
                                     @Value("${app.analytics.ingestion.enabled:false}") boolean ingestionEnabled) {
        this.queries = queries;
        this.clock = clock;
        this.ingestionEnabled = ingestionEnabled;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(DashboardRequest request) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, AnalyticsMaintenanceService.VIETNAM);
        List<EventViolation> errors = new ArrayList<>();
        LocalDate to = request.to() == null ? today : request.to();
        LocalDate from = request.from() == null ? to.minusDays(DEFAULT_DAYS - 1L) : request.from();
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1 || days > MAX_DAYS) errors.add(new EventViolation("from", "INVALID_WINDOW", "Khoảng thời gian từ 1 đến " + MAX_DAYS + " ngày."));
        if (to.isAfter(today)) errors.add(new EventViolation("to", "INVALID_WINDOW", "Ngày kết thúc không được ở tương lai."));
        String device = blankToNull(request.device());
        String area = blankToNull(request.area());
        String source = blankToNull(request.source());
        if (device != null && !DEVICES.contains(device)) errors.add(new EventViolation("device", "INVALID_FILTER", "device: mobile, tablet hoặc desktop."));
        if (area != null && !AREA.matcher(area).matches()) errors.add(new EventViolation("area", "INVALID_FILTER", "area là mã quận/huyện (chữ số)."));
        if (source != null && !SOURCE.matcher(source).matches()) errors.add(new EventViolation("source", "INVALID_FILTER", "source là utm_source viết thường hoặc (direct)."));
        if (!errors.isEmpty()) throw new InvalidEventsException(errors);

        Filters filters = new Filters(device, area, source);
        Instant start = from.atStartOfDay(AnalyticsMaintenanceService.VIETNAM).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(AnalyticsMaintenanceService.VIETNAM).toInstant();
        Window window = new Window(start, end.isAfter(now) ? now : end, from, to);

        AnalyticsDashboardQueries.WebVolume pipeline = queries.webVolume(window, new Filters(null, null, null));
        String webOff = pipeline.events() > 0 ? null : ingestionEnabled
                ? "Chưa có sự kiện web nào trong khoảng này (chỉ ghi nhận khi người dùng đồng ý phân tích)."
                : "Thu thập sự kiện web đang tắt (APP_ANALYTICS_INGESTION_ENABLED=false).";
        String webNoArea = area != null ? "Chỉ số này không gắn khu vực; bỏ bộ lọc khu vực để xem." : null;
        String serverNoWebFilter = filters.webOnly() ? "Dữ liệu phía máy chủ không phân tách theo thiết bị hoặc nguồn truy cập." : null;

        List<Funnel> funnels = new ArrayList<>();
        List<NamedMetric> metrics = new ArrayList<>();

        // Search → detail → lead (sessions)
        String searchReason = firstNonNull(webOff, webNoArea);
        AnalyticsDashboardQueries.SearchFunnel search = searchReason == null ? queries.searchFunnel(window, filters) : null;
        funnels.add(new Funnel("search", "Tìm kiếm → xem tin → liên hệ",
                "Số phiên (đã đồng ý phân tích) có tìm kiếm; trong đó có xem chi tiết tin; mở form liên hệ; và người dùng của phiên "
                        + "đã gửi yêu cầu liên hệ trong khoảng thời gian. Mỗi phiên được đếm một lần ở mỗi bước.", WEB,
                steps(searchReason, search == null ? null : new long[] {search.searchSessions(), search.detailSessions(),
                                search.leadFormSessions(), search.leadSessions()},
                        new String[][] {{"search", "Phiên có tìm kiếm"}, {"detail", "Xem chi tiết tin"}, {"leadForm", "Mở form liên hệ"},
                                {"lead", "Gửi liên hệ (đã đăng nhập)"}})));
        metrics.add(named("searchToDetail", "Tìm kiếm → xem tin", "funnel", "Phiên có xem chi tiết / phiên có tìm kiếm.", WEB,
                search == null ? Metric.notMeasured("percent", searchReason) : Metric.percent(search.detailSessions(), search.searchSessions())));
        metrics.add(named("detailToLead", "Xem tin → liên hệ", "funnel",
                "Phiên có gửi liên hệ / phiên có xem chi tiết (sau tìm kiếm).", WEB,
                search == null ? Metric.notMeasured("percent", searchReason) : Metric.percent(search.leadSessions(), search.detailSessions())));

        // F17.4 KYC drop-off
        AnalyticsDashboardQueries.KycFunnel kyc = searchReason == null ? queries.kycFunnel(window, filters) : null;
        funnels.add(new Funnel("kyc", "Form liên hệ → yêu cầu xác minh → gửi", "Phiên mở form liên hệ; trong đó được yêu cầu "
                + "xác minh danh tính (KYC); và vẫn gửi được liên hệ sau đó. Tỷ lệ bỏ cuộc sau KYC = 1 − bước 3/bước 2.", WEB,
                steps(searchReason, kyc == null ? null : new long[] {kyc.formSessions(), kyc.kycShownSessions(), kyc.submittedAfterKyc()},
                        new String[][] {{"formOpened", "Mở form liên hệ"}, {"kycShown", "Được yêu cầu xác minh"},
                                {"submittedAfterKyc", "Gửi liên hệ sau xác minh"}})));
        metrics.add(named("kycAbandonment", "Bỏ cuộc sau yêu cầu xác minh", "funnel",
                "Phiên được yêu cầu KYC nhưng không gửi liên hệ / phiên được yêu cầu KYC.", WEB,
                kyc == null ? Metric.notMeasured("percent", searchReason)
                        : Metric.percent(kyc.kycShownSessions() - kyc.submittedAfterKyc(), kyc.kycShownSessions())));
        // F17.4 (W6): the "before KYC" side — the same form without the KYC wall — so the cost of KYC is the difference.
        long withoutKyc = kyc == null ? 0 : kyc.formSessions() - kyc.kycShownSessions();
        long submittedWithoutKyc = kyc == null ? 0 : kyc.submittedSessions() - kyc.submittedAfterKyc();
        metrics.add(named("leadAbandonmentWithoutKyc", "Bỏ cuộc trước bước xác minh (không gặp KYC)", "funnel",
                "Phiên mở form liên hệ, không bị yêu cầu KYC và không gửi liên hệ / phiên mở form liên hệ không bị yêu cầu KYC. "
                        + "So với \"Bỏ cuộc sau yêu cầu xác minh\": phần chênh lệch là mức bỏ cuộc do KYC.", WEB,
                kyc == null ? Metric.notMeasured("percent", searchReason)
                        : Metric.percent(withoutKyc - submittedWithoutKyc, withoutKyc)));
        // Server side: measured from the lead API itself, with or without web collection and consent.
        AnalyticsDashboardQueries.KycServerFunnel kycServer = serverNoWebFilter == null ? queries.kycServerFunnel(window) : null;
        metrics.add(named("kycAbandonmentServer", "Bỏ cuộc sau khi bị chặn vì chưa KYC (máy chủ)", "funnel",
                "Người dùng bị API liên hệ từ chối vì chưa xác minh danh tính trong khoảng thời gian và không gửi được liên hệ nào "
                        + "sau lần bị chặn đầu tiên / người dùng bị chặn. Ghi nhận phía máy chủ, không phụ thuộc đồng ý phân tích.", SERVER,
                kycServer == null ? Metric.notMeasured("percent", serverNoWebFilter)
                        : Metric.percent(kycServer.blockedUsers() - kycServer.submittedAfterBlock(), kycServer.blockedUsers())));

        // Zero result
        AnalyticsDashboardQueries.ZeroResults zero = searchReason == null ? queries.zeroResults(window, filters) : null;
        Metric zeroRate = zero == null ? Metric.notMeasured("percent", searchReason) : Metric.percent(zero.zeroResultSearches(), zero.searchesWithCount());
        metrics.add(named("zeroResultRate", "Tìm kiếm không có kết quả", "search",
                "Lượt tìm kiếm trả về 0 tin / lượt tìm kiếm có số kết quả.", WEB, zeroRate));

        // Posting funnel (database)
        AnalyticsDashboardQueries.PostingFunnel posting = serverNoWebFilter == null ? queries.postingFunnel(window, filters) : null;
        funnels.add(new Funnel("posting", "Tạo tin → gửi duyệt → được duyệt", "Tin tạo mới trong khoảng thời gian (trừ dữ liệu mẫu và "
                + "tài khoản nội bộ): đã gửi duyệt ít nhất một lần; đã có phiên bản được duyệt; hiện đang hiển thị.", DATABASE,
                steps(serverNoWebFilter, posting == null ? null : new long[] {posting.created(), posting.submitted(), posting.approved(), posting.activeNow()},
                        new String[][] {{"created", "Tin được tạo"}, {"submitted", "Đã gửi duyệt"}, {"approved", "Được duyệt"},
                                {"active", "Đang hiển thị"}})));
        metrics.add(named("medianHoursToApproval", "Thời gian duyệt (trung vị)", "supply",
                "Trung vị số giờ từ lần gửi duyệt đầu tiên đến lần được duyệt đầu tiên.", DATABASE,
                posting == null ? Metric.notMeasured("hours", serverNoWebFilter)
                        : posting.medianHoursToApproval() == null ? Metric.notMeasured("hours", "Chưa có tin nào được duyệt trong khoảng này.")
                        : Metric.value(posting.medianHoursToApproval(), "hours")));

        // Lead → response → qualified → appointment (database)
        AnalyticsDashboardQueries.LeadFunnel leads = serverNoWebFilter == null ? queries.leadFunnel(window, filters) : null;
        funnels.add(new Funnel("lead", "Liên hệ → phản hồi → đủ điều kiện → hẹn xem", "Yêu cầu liên hệ tạo trong khoảng thời gian "
                + "(trừ spam và tài khoản nội bộ): đã được phản hồi; được đánh giá đủ điều kiện; có lịch hẹn hai bên xác nhận; "
                + "lịch hẹn đã diễn ra.", DATABASE,
                steps(serverNoWebFilter, leads == null ? null : new long[] {leads.leads(), leads.responded(), leads.qualified(),
                                leads.appointmentConfirmed(), leads.appointmentCompleted()},
                        new String[][] {{"leads", "Yêu cầu liên hệ"}, {"responded", "Đã phản hồi"}, {"qualified", "Đủ điều kiện"},
                                {"appointmentConfirmed", "Hẹn xem đã xác nhận"}, {"appointmentCompleted", "Hẹn xem đã diễn ra"}})));
        Metric noServer = serverNoWebFilter == null ? null : Metric.notMeasured("percent", serverNoWebFilter);
        metrics.add(named("medianFirstResponseMinutes", "Thời gian phản hồi (trung vị)", "sla",
                "Trung vị số phút từ khi nhận yêu cầu đến phản hồi đầu tiên.", DATABASE, minutes(leads, true, serverNoWebFilter)));
        metrics.add(named("p90FirstResponseMinutes", "Thời gian phản hồi (p90)", "sla",
                "90% yêu cầu được phản hồi trong số phút này.", DATABASE, minutes(leads, false, serverNoWebFilter)));
        metrics.add(named("responseWithinSla", "Phản hồi đúng hạn", "sla",
                "Yêu cầu được phản hồi trong mục tiêu SLA của người đăng (mặc định 30 phút) / tổng yêu cầu.", DATABASE,
                leads == null ? noServer : Metric.percent(leads.respondedWithinTarget(), leads.leads())));
        metrics.add(named("qualifiedShare", "Liên hệ đủ điều kiện", "quality",
                "Yêu cầu được đánh giá đủ điều kiện / yêu cầu đã được đánh giá.", DATABASE,
                leads == null ? noServer : Metric.percent(leads.qualified(), leads.assessed())));
        metrics.add(named("leadToAppointment", "Liên hệ → hẹn xem", "funnel",
                "Yêu cầu có lịch hẹn hai bên xác nhận / tổng yêu cầu.", DATABASE,
                leads == null ? noServer : Metric.percent(leads.appointmentConfirmed(), leads.leads())));
        metrics.add(named("appointmentHeld", "Hẹn xem diễn ra", "funnel",
                "Lịch hẹn đã diễn ra / lịch hẹn đã có kết quả (diễn ra hoặc vắng mặt).", DATABASE,
                leads == null ? noServer : Metric.percent(leads.appointmentCompleted(), leads.appointmentCompleted() + leads.appointmentNoShow())));

        // North star (P-13)
        AnalyticsDashboardQueries.NorthStar north = serverNoWebFilter == null ? queries.northStar(window, filters) : null;
        double weeks = Math.max(1.0, days / 7.0);
        metrics.add(named("northStar", "Hẹn xem hai bên xác nhận / người tìm đủ điều kiện / tuần", "north-star",
                "Số lịch hẹn được hai bên xác nhận trong khoảng ÷ số người tìm có liên hệ đủ điều kiện ÷ số tuần.", DATABASE,
                north == null ? Metric.notMeasured("ratio", serverNoWebFilter)
                        : north.qualifiedSeekers() == 0 ? new Metric(Metric.Status.NOT_MEASURED, null, "ratio",
                        "Chưa có người tìm nào có liên hệ đủ điều kiện.", north.confirmedAppointments(), 0L)
                        : new Metric(Metric.Status.MEASURED, Math.round(north.confirmedAppointments() / (double) north.qualifiedSeekers() / weeks * 100.0) / 100.0,
                        "ratio", null, north.confirmedAppointments(), north.qualifiedSeekers())));
        metrics.add(named("confirmedAppointments", "Lịch hẹn hai bên xác nhận", "north-star",
                "Lịch hẹn được xác nhận trong khoảng thời gian (trừ tài khoản nội bộ).", DATABASE,
                north == null ? Metric.notMeasured("count", serverNoWebFilter) : Metric.count(north.confirmedAppointments())));

        // Supply
        AnalyticsDashboardQueries.Supply supply = serverNoWebFilter == null ? queries.supply(window, filters) : null;
        metrics.add(named("soldStillListed", "Tin bị báo đã bán/cho thuê", "supply",
                "Tin bị báo “đã bán/cho thuê nhưng còn treo” trong khoảng / tin đang hiển thị.", DATABASE,
                supply == null ? noServer : Metric.percent(supply.fakeSoldReports(), supply.activeListings())));

        // Broker (P-13)
        String brokerReason = firstNonNull(serverNoWebFilter, area != null ? "Chỉ số gói dịch vụ không gắn khu vực." : null);
        AnalyticsDashboardQueries.Broker broker = brokerReason == null ? queries.broker(window) : null;
        metrics.add(named("costPerQualifiedLead", "Chi phí / liên hệ đủ điều kiện", "broker",
                "Doanh thu gói đã duyệt trong khoảng ÷ liên hệ đủ điều kiện trên tin của người đã mua gói.", DATABASE,
                broker == null ? Metric.notMeasured("vnd", brokerReason)
                        : broker.qualifiedLeadsOfPayers() == 0 ? new Metric(Metric.Status.NOT_MEASURED, null, "vnd",
                        "Chưa có liên hệ đủ điều kiện trên tin của người mua gói.", broker.approvedRevenueVnd(), 0L)
                        : new Metric(Metric.Status.MEASURED, (double) Math.round((double) broker.approvedRevenueVnd() / broker.qualifiedLeadsOfPayers()),
                        "vnd", null, broker.approvedRevenueVnd(), broker.qualifiedLeadsOfPayers())));
        metrics.add(named("renewalRate", "Mua lại / gia hạn gói", "broker",
                "Người có đơn gói được duyệt trong khoảng và đã từng có đơn được duyệt trước đó / người có đơn được duyệt trong khoảng.",
                DATABASE, broker == null ? Metric.notMeasured("percent", brokerReason) : Metric.percent(broker.returningPayers(), broker.payers())));

        // Return visits + cohorts (aggregates)
        AnalyticsDashboardQueries.ReturnVisits returns = searchReason == null ? queries.returnVisits(window, filters) : null;
        metrics.add(named("returnRate", "Quay lại", "retention",
                "Thiết bị hoạt động từ 2 ngày trở lên trong khoảng / thiết bị hoạt động (đã đồng ý phân tích).", AGGREGATE,
                returns == null ? Metric.notMeasured("percent", searchReason) : Metric.percent(returns.returningDevices(), returns.devices())));
        Cohorts cohorts = cohorts(searchReason, searchReason == null ? queries.cohorts(window, filters) : List.of(), from, to);

        // Web vitals (F15.3)
        List<VitalRow> vitals = new ArrayList<>();
        if (searchReason == null) {
            for (AnalyticsDashboardQueries.WebVital vital : queries.webVitals(window, filters)) vitals.add(vital(vital));
        }

        Map<String, List<AnalyticsDashboardQueries.Breakdown>> breakdowns = new LinkedHashMap<>();
        List<AnalyticsDashboardQueries.TrendDay> trend = List.of();
        if (webOff == null) {
            for (String dimension : List.of("source", "device", "area")) breakdowns.put(dimension, queries.breakdown(window, filters, dimension, 20));
            trend = fillDays(queries.trend(window, filters), from, to);
        }

        AnalyticsDashboardQueries.Freshness fresh = queries.freshness(now);
        List<Alert> alerts = alerts(now, fresh, pipeline, zero, leads, vitals);
        return new Dashboard(new WindowView(from, to, (int) days, AnalyticsMaintenanceService.VIETNAM.getId()), filters, now,
                new Collection(ingestionEnabled, pipeline.events(), pipeline.sessions(),
                        "Chỉ gồm khách đã đồng ý phân tích; đã loại bot và lưu lượng nội bộ."),
                new Freshness(fresh.latestWebEventAt(), fresh.aggregatesComputedAt(),
                        "Sự kiện web gửi theo lô (≤ 5 giây, hoặc khi rời trang); số liệu thô cập nhật ngay khi nhận.",
                        "Nguồn truy cập, khu vực, thiết bị, xu hướng và cohort tổng hợp mỗi giờ (phút thứ 7); sự kiện đến muộn "
                                + "được tổng hợp lại hằng đêm cho 8 ngày gần nhất."),
                funnels, metrics, cohorts, vitals, searchReason, breakdowns, trend, alerts);
    }

    private static List<FunnelStep> steps(@Nullable String reason, @Nullable long[] counts, String[][] labels) {
        List<FunnelStep> steps = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            if (counts == null) {
                steps.add(new FunnelStep(labels[i][0], labels[i][1], Metric.notMeasured("count", reason),
                        i == 0 ? null : Metric.notMeasured("percent", reason)));
            } else {
                steps.add(new FunnelStep(labels[i][0], labels[i][1], Metric.count(counts[i]),
                        i == 0 ? null : Metric.percent(counts[i], counts[i - 1])));
            }
        }
        return steps;
    }

    private static NamedMetric named(String key, String label, String group, String definition, String source, Metric metric) {
        return new NamedMetric(key, label, group, definition, source, metric);
    }

    private static Metric minutes(@Nullable AnalyticsDashboardQueries.LeadFunnel leads, boolean median, @Nullable String reason) {
        if (leads == null) return Metric.notMeasured("minutes", reason);
        Double value = median ? leads.medianResponseMinutes() : leads.p90ResponseMinutes();
        return value == null ? Metric.notMeasured("minutes", "Chưa có yêu cầu nào được phản hồi trong khoảng này.") : Metric.value(value, "minutes");
    }

    private static Cohorts cohorts(@Nullable String reason, List<AnalyticsDashboardQueries.CohortCell> cells, LocalDate from, LocalDate to) {
        String definition = "Thiết bị theo tuần xuất hiện lần đầu (thứ Hai); % còn hoạt động ở tuần 0, 1, 2… sau đó. Chỉ khách đã "
                + "đồng ý phân tích; giữ tối đa 90 ngày.";
        if (reason != null) return new Cohorts(Metric.Status.NOT_MEASURED, reason, definition, List.of());
        Map<LocalDate, Map<Integer, Long>> byWeek = new TreeMap<>();
        for (AnalyticsDashboardQueries.CohortCell cell : cells) {
            byWeek.computeIfAbsent(cell.cohortWeek(), key -> new TreeMap<>()).put(cell.weekOffset(), cell.devices());
        }
        List<CohortRow> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<Integer, Long>> entry : byWeek.entrySet()) {
            long size = entry.getValue().getOrDefault(0, 0L);
            int maxOffset = (int) Math.max(0, ChronoUnit.WEEKS.between(entry.getKey(), to));
            List<Double> retention = new ArrayList<>();
            for (int offset = 0; offset <= Math.min(maxOffset, 12); offset++) {
                long active = entry.getValue().getOrDefault(offset, 0L);
                retention.add(size == 0 ? null : Math.round(1000.0 * active / size) / 10.0);
            }
            rows.add(new CohortRow(entry.getKey(), size, retention));
        }
        return new Cohorts(Metric.Status.MEASURED, rows.isEmpty() ? "Chưa có thiết bị mới trong khoảng này." : null, definition, rows);
    }

    /** Core Web Vitals thresholds (web.dev): good ≤ first, poor > second. */
    private static VitalRow vital(AnalyticsDashboardQueries.WebVital vital) {
        double good;
        double poor;
        String unit;
        switch (vital.metric()) {
            case "LCP" -> { good = 2500; poor = 4000; unit = "ms"; }
            case "INP" -> { good = 200; poor = 500; unit = "ms"; }
            case "CLS" -> { good = 0.1; poor = 0.25; unit = "score"; }
            default -> { good = 800; poor = 1800; unit = "ms"; }
        }
        String rating = vital.p75() <= good ? "good" : vital.p75() <= poor ? "needs-improvement" : "poor";
        return new VitalRow(vital.metric(), vital.device().isEmpty() ? "unknown" : vital.device(), Metric.value(vital.p75(), unit),
                vital.samples(), good, poor, rating);
    }

    private static List<AnalyticsDashboardQueries.TrendDay> fillDays(List<AnalyticsDashboardQueries.TrendDay> rows, LocalDate from, LocalDate to) {
        Map<LocalDate, AnalyticsDashboardQueries.TrendDay> byDay = new TreeMap<>();
        for (AnalyticsDashboardQueries.TrendDay row : rows) byDay.put(row.day(), row);
        List<AnalyticsDashboardQueries.TrendDay> filled = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            filled.add(byDay.getOrDefault(day, new AnalyticsDashboardQueries.TrendDay(day, 0, 0, 0, 0)));
        }
        return filled;
    }

    private List<Alert> alerts(Instant now, AnalyticsDashboardQueries.Freshness fresh, AnalyticsDashboardQueries.WebVolume pipeline,
                               @Nullable AnalyticsDashboardQueries.ZeroResults zero, @Nullable AnalyticsDashboardQueries.LeadFunnel leads,
                               List<VitalRow> vitals) {
        List<Alert> alerts = new ArrayList<>();
        if (!ingestionEnabled) {
            alerts.add(new Alert("WARNING", "INGESTION_DISABLED",
                    "Thu thập sự kiện web đang tắt trên máy chủ này: các chỉ số web hiển thị “Chưa đo”."));
        } else if (fresh.latestWebEventAt() == null || fresh.latestWebEventAt().isBefore(now.minus(Duration.ofHours(24)))) {
            alerts.add(new Alert("WARNING", "NO_RECENT_WEB_EVENTS", "Không nhận được sự kiện web nào trong 24 giờ qua."));
        }
        if (pipeline.events() > 0 && (fresh.aggregatesComputedAt() == null || fresh.aggregatesComputedAt().isBefore(now.minus(Duration.ofHours(3))))) {
            alerts.add(new Alert("WARNING", "AGGREGATES_STALE", "Bảng tổng hợp chưa được cập nhật trong 3 giờ qua (tác vụ analytics-hourly)."));
        }
        if (leads != null && leads.leads() >= 10 && leads.respondedWithinTarget() * 100 < leads.leads() * 80) {
            alerts.add(new Alert("WARNING", "LEAD_SLA", "Dưới 80% yêu cầu liên hệ được phản hồi đúng hạn."));
        }
        if (zero != null && zero.searchesWithCount() >= 50 && zero.zeroResultSearches() * 100 > zero.searchesWithCount() * 30) {
            alerts.add(new Alert("INFO", "ZERO_RESULTS", "Hơn 30% lượt tìm kiếm không có kết quả: xem lại nguồn cung hoặc bộ lọc."));
        }
        for (VitalRow vital : vitals) {
            if ("poor".equals(vital.rating()) && vital.samples() >= 20) {
                alerts.add(new Alert("WARNING", "WEB_VITAL_POOR", vital.metric() + " p75 trên " + vital.device() + " ở mức kém."));
            }
        }
        return alerts;
    }

    @Nullable
    private static String firstNonNull(@Nullable String first, @Nullable String second) {
        return first != null ? first : second;
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
