"""Recorded drill failures must fail the CLI while preserving its evidence summary."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

REPORT = Path(__file__).with_name("ops_drill_report.py")
spec = importlib.util.spec_from_file_location("ops_drill_report", REPORT)
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


def recovery_fixture():
    return {
        "restore_drill": "PASS",
        "hostloss_media_references": "PASS",
        "last_marker_at_loss": "12 2026-10-03T12:00:12.000Z",
        "pitr_restored_last_marker": "11 2026-10-03T12:00:11.000Z",
        "hostloss_restored_last_marker": "10 2026-10-03T12:00:10.000Z",
        "pitr_data_restore_seconds": "10.5",
        "pitr_rto_first_page_seconds": "20.0",
        "pitr_rto_seconds": "25.2",
        "hostloss_db_restore_seconds": "14.0",
        "hostloss_media_restore_seconds": "3.0",
        "hostloss_rto_first_page_seconds": "40.0",
        "hostloss_rto_seconds": "47.5",
    }


def rollback_fixture(refs=("abc1234", "def56789")):
    values = {
        "previous_refs": " ".join(refs),
        "verify_headers_local": "PASS",
        "backend_only_restart_api": "PASS",
        "backend_only_restart_serving_seconds": "11.0",
        "daemon_graceful_first_page_seconds": "40.0",
        "daemon_graceful_serving_seconds": "45.0",
        "daemon_hard_first_page_seconds": "42.0",
        "daemon_hard_serving_seconds": "48.0",
        # This stopped control container is intentional; a running ratio isn't an app check.
        "daemon_hard_running": "13/14",
        "edge_ip_hint": "skipped (DRILL_QUICK_TUNNEL=0)",
    }
    labels = ["current", "after_restarts"]
    for ref in refs:
        for label in (f"rollback_{ref}", f"forward_after_{ref}"):
            values[f"{label}_start"] = "PASS"
            values[f"{label}_healthy_seconds"] = "15.0"
            labels.append(label)
    for label in labels:
        for suffix in ("backend_health", "listing_page", "search_v1", "login_me"):
            values[f"{label}_{suffix}"] = "200"
    return values


class OutcomeTests(unittest.TestCase):
    def assert_invalid(self, kind, values, key):
        errors = report.validate(kind, values)
        self.assertTrue(any(key in error for error in errors), errors)

    def run_cli(self, kind, values):
        with tempfile.TemporaryDirectory() as directory:
            result_file = Path(directory) / "results.env"
            result_file.write_text("".join(f"{key}={value}\n" for key, value in values.items()), encoding="utf-8")
            return subprocess.run([sys.executable, str(REPORT), kind, str(result_file)],
                                  text=True, capture_output=True, check=False)

    def test_valid_minimal_fixtures_succeed_without_optional_edge_or_container_claims(self):
        for kind, values in (("recovery", recovery_fixture()), ("rollback", rollback_fixture())):
            with self.subTest(kind=kind):
                self.assertEqual(report.validate(kind, values), [])
                result = self.run_cli(kind, values)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("## Outcome\n\nPASS", result.stdout)

    def test_recovery_checks_cannot_fail_disappear_or_be_skipped(self):
        for key in ("restore_drill", "hostloss_media_references"):
            for value in (None, "FAIL", "GAP", "SKIPPED"):
                with self.subTest(key=key, value=value):
                    values = recovery_fixture()
                    if value is None:
                        del values[key]
                    else:
                        values[key] = value
                    self.assert_invalid("recovery", values, key)

    def test_each_app_endpoint_and_release_is_required_to_return_200(self):
        baseline = rollback_fixture()
        http_keys = [key for key in baseline if key.endswith(("_backend_health", "_listing_page", "_search_v1", "_login_me"))]
        for key in http_keys:
            for value in (None, "503", "login-failed"):
                with self.subTest(key=key, value=value):
                    values = baseline.copy()
                    if value is None:
                        del values[key]
                    else:
                        values[key] = value
                    self.assert_invalid("rollback", values, key)

    def test_extra_app_checks_and_explicit_failure_cannot_hide_in_summary(self):
        for key, value in (("unexpected_listing_page", "404"), ("extra_verification", "FAIL (details)")):
            values = rollback_fixture()
            values[key] = value
            self.assert_invalid("rollback", values, key)

    def test_every_dynamic_rollback_and_forward_start_is_required(self):
        # Ref count and identities may change after main moves or history is rewritten.
        for refs in (("1234",), ("abc1234", "def56789", "0123456789abcdef")):
            baseline = rollback_fixture(refs)
            self.assertEqual(report.validate("rollback", baseline), [])
            for key in (key for key in baseline if key.endswith("_start")):
                for value in (None, "FAIL"):
                    with self.subTest(refs=refs, key=key, value=value):
                        values = baseline.copy()
                        if value is None:
                            del values[key]
                        else:
                            values[key] = value
                        self.assert_invalid("rollback", values, key)

    def test_empty_or_unresolved_previous_refs_cannot_skip_rollback_evidence(self):
        for value in (None, "", "origin/main HEAD^1"):
            values = rollback_fixture()
            if value is None:
                del values["previous_refs"]
            else:
                values["previous_refs"] = value
            self.assert_invalid("rollback", values, "previous_refs")

    def test_daemon_recovery_rejects_missing_timeout_and_nonfinite_evidence(self):
        for label in ("daemon_graceful", "daemon_hard"):
            for suffix in ("first_page_seconds", "serving_seconds"):
                key = f"{label}_{suffix}"
                for value in (None, "not serving after 600 s", "nan", "inf", "-1"):
                    with self.subTest(key=key, value=value):
                        values = rollback_fixture()
                        if value is None:
                            del values[key]
                        else:
                            values[key] = value
                        self.assert_invalid("rollback", values, key)
        values = rollback_fixture()
        values["daemon_hard_first_page_seconds"] = "650"
        values["daemon_hard_serving_seconds"] = "660"
        self.assertEqual(report.validate("rollback", values), [])

    def test_required_backend_restart_and_header_checks_cannot_be_missing_or_fail(self):
        for key in ("backend_only_restart_api", "verify_headers_local"):
            for value in (None, "FAIL"):
                values = rollback_fixture()
                if value is None:
                    del values[key]
                else:
                    values[key] = value
                self.assert_invalid("rollback", values, key)

    def test_recovery_markers_and_elapsed_times_cannot_be_missing_or_malformed(self):
        baseline = recovery_fixture()
        for key in (key for key in baseline if "marker" in key or key.endswith("_seconds")):
            for value in (None, "not-a-measurement"):
                with self.subTest(key=key, value=value):
                    values = baseline.copy()
                    if value is None:
                        del values[key]
                    else:
                        values[key] = value
                    self.assert_invalid("recovery", values, key)
        values = baseline.copy()
        values["pitr_rto_first_page_seconds"] = "30"
        self.assert_invalid("recovery", values, "pitr")

    def test_failed_cli_preserves_summary_and_emits_nonzero_diagnostics(self):
        for kind, values, key in (("recovery", recovery_fixture(), "restore_drill"),
                                  ("rollback", rollback_fixture(), "rollback_abc1234_start")):
            with self.subTest(kind=kind):
                values[key] = "FAIL"
                result = self.run_cli(kind, values)
                self.assertEqual(result.returncode, 1)
                self.assertIn("# Recovery drill" if kind == "recovery" else "# Rollback", result.stdout)
                self.assertIn("## Outcome\n\nFAIL", result.stdout)
                self.assertIn(key, result.stderr)
        values = recovery_fixture()
        values["pitr_restored_last_marker"] = "bad marker"
        result = self.run_cli("recovery", values)
        self.assertEqual(result.returncode, 1)
        self.assertIn("invalid rows", result.stdout)
        self.assertIn("pitr_restored_last_marker", result.stderr)


if __name__ == "__main__":
    unittest.main()
