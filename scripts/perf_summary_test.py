"""Prevent incomplete/invalid benchmark evidence from producing an apparently valid report."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('perf_summary', Path(__file__).with_name('perf-summary.py'))
summary_parser = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary_parser)


class SummaryEvidenceTests(unittest.TestCase):
    def test_successful_count_requires_a_positive_finite_integer(self):
        for value in [None, True, 0, -1, 1.5, float('nan'), float('inf'), '600']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                summary_parser.draft_count({'metrics': {'drafts_created': {'values': {'count': value}}}})
        self.assertEqual(summary_parser.draft_count({'metrics': {'drafts_created': {'values': {'count': 600}}}}), 600)

    def test_report_keeps_separate_endpoint_latency_and_error_values(self):
        metrics = {'drafts_created': {'values': {'count': 600}}, 'dropped_iterations': {'values': {'count': 0}}}
        for scenario, latency, error in [('reads', 25, 0), ('writes', 120, .025)]:
            metrics[f'http_req_duration{{scenario:{scenario}}}'] = {'values': {
                'med': latency, 'p(95)': latency * 2, 'p(99)': latency * 3}}
            metrics[f'http_req_failed{{scenario:{scenario}}}'] = {'values': {'rate': error}}
        output = summary_parser.report({'metrics': metrics})
        self.assertIn('| reads | 25.00 | 50.00 | 75.00 | 0.0000% |', output)
        self.assertIn('| writes | 120.00 | 240.00 | 360.00 | 2.5000% |', output)
        del metrics['http_req_duration{scenario:writes}']['values']['p(99)']
        with self.assertRaises(ValueError):
            summary_parser.report({'metrics': metrics})

    def test_error_rate_outside_zero_to_one_is_rejected(self):
        for rate in [-.1, 1.1]:
            with self.assertRaises(ValueError):
                summary_parser.metric({'metrics': {'errors': {'values': {'rate': rate}}}}, 'errors', 'rate')

    def test_fault_and_recovery_require_the_expected_engine_on_every_read(self):
        metrics = {'read_attempts': {'values': {'count': 6000}}, 'degraded_reads': {'values': {'count': 6000}}}
        summary_parser.verify_engine_state({'metrics': metrics}, 1)
        with self.assertRaises(ValueError):
            summary_parser.verify_engine_state({'metrics': metrics}, 0)
        metrics['degraded_reads']['values']['count'] = 5999
        with self.assertRaises(ValueError):
            summary_parser.verify_engine_state({'metrics': metrics}, 1)
        metrics['degraded_reads']['values']['count'] = 0
        summary_parser.verify_engine_state({'metrics': metrics}, 0)

    def test_redis_outage_evidence_cannot_be_missing_or_claim_recovery_while_down(self):
        sample = 'bds_ratelimit_redis_available{application="bds"} 0.0\n'
        summary_parser.verify_redis_state(sample, 0)
        for bad in ['', sample, sample + sample, 'bds_ratelimit_redis_available NaN\n']:
            with self.assertRaises(ValueError):
                summary_parser.verify_redis_state(bad, 1)
        summary_parser.verify_redis_state('bds_ratelimit_redis_available 1.0\n', 1)


if __name__ == '__main__':
    unittest.main()
