package com.company.bds.search.api;

import com.company.bds.search.application.SearchIndexAdministration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN: inspect and rebuild the search index generations (route secured in {@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v2/admin/search/index")
public class AdminSearchIndexController {
    private final SearchIndexAdministration administration;

    public AdminSearchIndexController(SearchIndexAdministration administration) {
        this.administration = administration;
    }

    @GetMapping
    public SearchIndexAdministration.Status status() {
        return administration.status();
    }

    @PostMapping("/rebuild")
    public ResponseEntity<SearchIndexAdministration.Status> rebuild() {
        return ResponseEntity.accepted().body(administration.startRebuild());
    }

    @PostMapping("/rollback")
    public SearchIndexAdministration.Status rollback() {
        return administration.rollback();
    }

    @PostMapping("/cleanup")
    public SearchIndexAdministration.Status cleanup() {
        return administration.cleanup();
    }
}
