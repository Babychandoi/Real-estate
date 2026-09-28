package com.company.bds.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module and layer boundaries of the modular monolith (PROJECT_CODE_RULES §4.3, audit F22.5).
 *
 * <p>A module is the first package below {@code com.company.bds}. Layered modules have {@code api}, {@code application},
 * {@code domain} and {@code infrastructure}; a few older modules are still flat (one package) and only get the
 * cross-module rules. {@code shared} is the kernel every module may use.
 *
 * <p>Known violations are listed one by one in {@link #ALLOWED} with the reason they are tolerated. A new violation
 * fails the build, and so does an allowance that no longer matches anything (delete it once the code is fixed), so the
 * list can only shrink.
 */
class ArchitectureTests {

    private static final String ROOT = "com.company.bds.";
    private static final Set<String> LAYERS = Set.of("api", "application", "domain", "infrastructure");

    /** "origin class -> target class" (top-level classes) → why it is tolerated. */
    private static final Map<String, String> ALLOWED = new LinkedHashMap<>();

    static {
        allow("com.company.bds.shared.error.GlobalExceptionHandler",
                "com.company.bds.listing.domain.exception.ListingDomainException",
                "the central Problem Details mapper translates the listing module's domain exceptions; the domain must "
                        + "stay free of HTTP types so it cannot extend ApiException");
        allow("com.company.bds.shared.error.GlobalExceptionHandler",
                "com.company.bds.listing.domain.exception.ListingValidationException", "same as ListingDomainException");
        allow("com.company.bds.shared.error.GlobalExceptionHandler",
                "com.company.bds.listing.domain.exception.ListingVersionConflictException", "same as ListingDomainException");
        allow("com.company.bds.shared.security.BearerTokenFilter", "com.company.bds.iam.application.AuthService",
                "the security filter chain authenticates bearer tokens through the iam use case (inbound adapter)");
        allow("com.company.bds.shared.security.BearerTokenFilter", "com.company.bds.iam.application.SessionService",
                "same: session revocation/expiry is checked per request");
        allow("com.company.bds.shared.security.AuditTrailFilter", "com.company.bds.iam.application.AuthService",
                "the audit filter resolves the actor of a request through the iam use case");
        allow("com.company.bds.shared.security.CurrentUser", "com.company.bds.iam.application.AuthService",
                "CurrentUser exposes the authenticated principal type defined by iam");
        allow("com.company.bds.engagement.api.SavedListingController", "com.company.bds.search.api.ListingSummaries",
                "saved listings reuse the public listing card contract of search v2 (contract §8) so both render the same card");
        allow("com.company.bds.engagement.api.SavedListingController",
                "com.company.bds.search.api.response.ListingV2Responses", "same: public listing card contract");
        allow("com.company.bds.engagement.api.ShortlistController", "com.company.bds.search.api.ListingSummaries",
                "same: public listing card contract");
        allow("com.company.bds.engagement.api.ShortlistController",
                "com.company.bds.search.api.response.ListingV2Responses", "same: public listing card contract");
        allow("com.company.bds.search.api.LegacySearchV1Controller",
                "com.company.bds.listing.api.response.ListingSummaryResponse",
                "the deprecated v1 search endpoint keeps the v1 listing summary shape byte-for-byte (backward compatibility)");
        allow("com.company.bds.broker.BrokerWorkspaceController", "com.company.bds.lead.api.request.LeadCommandRequests",
                "the broker workspace accepts the same lead command bodies as the lead API (one validated request type)");
    }

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.company.bds");
    }

    // ---- layers inside a module -------------------------------------------------------------------------------

    @Test
    void domainIsFreeOfFrameworksAndOuterLayers() {
        ArchRule rule = noClasses().that().resideInAPackage("com.company.bds.*.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..",
                        "com.fasterxml..", "..api..", "..application..", "..infrastructure..")
                .because("domain holds invariants only (PROJECT_CODE_RULES §4.3)");
        rule.check(classes);
    }

    @Test
    void applicationDependsOnPortsNotOnWebOrInfrastructure() {
        ArchRule rule = noClasses().that().resideInAPackage("com.company.bds.*.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..api..", "..infrastructure..", "jakarta.servlet..", "org.springframework.web..",
                        "org.springframework.http..")
                .because("application talks to the outside through its own ports; HTTP is the api layer's business");
        rule.check(classes);
    }

    @Test
    void apiDoesNotReachIntoInfrastructure() {
        ArchRule rule = noClasses().that().resideInAPackage("com.company.bds.*.api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("controllers call use cases, never repositories or JPA entities");
        rule.check(classes);
    }

    @Test
    void jpaEntitiesAndSpringDataRepositoriesLiveInInfrastructure() {
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("com.company.bds.*.infrastructure.persistence..")
                .check(classes);
        classes().that().areAssignableTo(org.springframework.data.repository.Repository.class).and().areInterfaces()
                .should().resideInAPackage("com.company.bds.*.infrastructure..")
                .check(classes);
    }

    @Test
    void controllersNeverExposeJpaEntities() {
        ArchRule rule = methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().arePublic()
                .should(new ArchCondition<>("not return or accept a JPA @Entity") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        List<JavaClass> involved = new java.util.ArrayList<>(method.getReturnType().getAllInvolvedRawTypes());
                        method.getParameterTypes().forEach(type -> involved.addAll(type.getAllInvolvedRawTypes()));
                        for (JavaClass type : involved) {
                            if (type.isAnnotatedWith(Entity.class)) {
                                events.add(SimpleConditionEvent.violated(method,
                                        method.getFullName() + " exposes entity " + type.getName()));
                            }
                        }
                    }
                });
        rule.check(classes);
    }

    @Test
    void layeredModulesKeepControllersInTheApiLayer() {
        classes().that().areAnnotatedWith(RestController.class)
                .and(new com.tngtech.archunit.base.DescribedPredicate<>("belong to a layered module") {
                    @Override
                    public boolean test(JavaClass input) {
                        return layer(input).isPresent();
                    }
                })
                .should().resideInAPackage("com.company.bds.*.api..")
                .check(classes);
    }

    @Test
    void noFieldInjection() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes);
    }

    // ---- boundaries between modules ---------------------------------------------------------------------------

    @Test
    void noModuleReachesIntoAnotherModulesInfrastructureOrWebLayer() {
        assertCrossModule((from, to) -> {
            String targetLayer = layerOf(to).orElse("");
            return targetLayer.equals("infrastructure") || targetLayer.equals("api");
        }, "another module's infrastructure (repositories, entities, adapters) or api (controllers, HTTP DTOs)");
    }

    @Test
    void sharedKernelDoesNotDependOnBusinessModules() {
        assertCrossModule((from, to) -> module(from).equals("shared"),
                "a business module from the shared kernel");
    }

    @Test
    void everyAllowanceStillMatchesARealDependency() {
        Set<String> actual = new TreeSet<>();
        for (JavaClass origin : classes) {
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                actual.add(topLevel(origin) + " -> " + topLevel(dependency.getTargetClass()));
            }
        }
        assertThat(ALLOWED.keySet())
                .as("allowances whose violation is gone — delete them from ArchitectureTests.ALLOWED")
                .allMatch(actual::contains);
    }

    private static void assertCrossModule(BiPredicate<JavaClass, JavaClass> forbidden, String what) {
        Set<String> violations = new TreeSet<>();
        for (JavaClass origin : classes) {
            if (!origin.getPackageName().startsWith(ROOT)) continue;
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (!target.getPackageName().startsWith(ROOT)) continue;
                if (module(origin).equals(module(target)) || module(target).equals("shared")) continue;
                if (!forbidden.test(origin, target)) continue;
                String key = topLevel(origin) + " -> " + topLevel(target);
                if (!ALLOWED.containsKey(key)) violations.add(key + "   (" + dependency.getDescription() + ")");
            }
        }
        assertThat(violations).as("classes depending on " + what).isEmpty();
    }

    private static void allow(String origin, String target, String reason) {
        ALLOWED.put(origin + " -> " + target, reason);
    }

    private static String module(JavaClass type) {
        String rest = type.getPackageName().substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /** The layer of a class of a layered module, e.g. {@code api} for {@code com.company.bds.lead.api.request.X}. */
    private static Optional<String> layerOf(JavaClass type) {
        String[] parts = type.getPackageName().substring(ROOT.length()).split("\\.");
        return parts.length > 1 && LAYERS.contains(parts[1]) ? Optional.of(parts[1]) : Optional.empty();
    }

    /** Whether the class's module is layered, i.e. has any class in a layer package. */
    private static Optional<String> layer(JavaClass type) {
        String module = module(type);
        boolean layered = classes.stream().anyMatch(other -> other.getPackageName().startsWith(ROOT)
                && module(other).equals(module) && layerOf(other).isPresent());
        return layered ? Optional.of(module) : Optional.empty();
    }

    private static String topLevel(JavaClass type) {
        String name = type.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }
}
