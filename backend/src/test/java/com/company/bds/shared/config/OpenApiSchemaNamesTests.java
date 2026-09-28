package com.company.bds.shared.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSchemaNamesTests {

    @Test
    void uniqueSimpleNamesAreKeptAsIs() {
        Map<String, String> renames = OpenApiSchemaNames.renames(List.of(
                "com.company.bds.shared.error.ProblemDetails",
                "com.company.bds.lead.api.request.CreateLeadRequest"));
        assertThat(renames).containsEntry("com.company.bds.shared.error.ProblemDetails", "ProblemDetails")
                .containsEntry("com.company.bds.lead.api.request.CreateLeadRequest", "CreateLeadRequest");
    }

    @Test
    void collidingSimpleNamesAreQualifiedByModule() {
        Map<String, String> renames = OpenApiSchemaNames.renames(List.of(
                "com.company.bds.lead.application.AppointmentService.Slot",
                "com.company.bds.lead.api.request.LeadCommandRequests.Slot"));
        assertThat(renames.values()).containsExactlyInAnyOrder("LeadSlot", "LeadSlot2");
    }

    @Test
    void everyNameIsUniqueAndDeterministic() {
        List<String> names = List.of(
                "com.company.bds.a.application.X.TeamMember",
                "com.company.bds.b.api.Y.TeamMember",
                "com.company.bds.a.api.Z.TeamMember",
                "com.company.bds.shared.error.ProblemDetails");
        Map<String, String> first = OpenApiSchemaNames.renames(names);
        Map<String, String> second = OpenApiSchemaNames.renames(names);
        assertThat(first).isEqualTo(second);
        assertThat(first.values()).doesNotHaveDuplicates();
    }

    @Test
    void alwaysProducesUniqueNamesEvenWhenModuleQualificationStillCollides() {
        // a generic wrapper's mangled name can still collide with the plain type's candidate after shortening
        List<String> names = List.of(
                "com.company.bds.lead.application.LeadInboxQuery.LeadItem",
                "com.company.bds.lead.application.LeadInboxQuery.PageResultCom.company.bds.lead.application.LeadInboxQuery.LeadItem");
        Map<String, String> renames = OpenApiSchemaNames.renames(names);
        assertThat(renames.values().stream().collect(Collectors.toSet())).hasSize(names.size());
    }
}
