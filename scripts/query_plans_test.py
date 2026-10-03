#!/usr/bin/env python3
"""Guard SQL extraction against the actual concatenated expiry predicate and unsupported Java expressions."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('query_plans', Path(__file__).with_name('query-plans.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class JavaSqlExtractionTests(unittest.TestCase):
    def test_single_literal_remains_compatible(self):
        self.assertEqual(module.java_string_constant('static final String SQL = " AND active";', 'SQL'), ' AND active')

    def test_concatenated_expiry_guard_is_sql_without_java_operators(self):
        source = '''static final String OWNER_ACTIVE =
            " AND EXISTS (SELECT 1 FROM users ou WHERE ou.id = listing_public_read.owner_id AND ou.status = 'ACTIVE')"
            + " AND NOT EXISTS (SELECT 1 FROM listings expired WHERE expired.id = listing_public_read.listing_id"
            + " AND expired.status = 'ACTIVE' AND expired.expires_at <= now())";'''
        extracted = module.java_string_constant(source, 'OWNER_ACTIVE')
        self.assertIn("AND NOT EXISTS (SELECT 1 FROM listings expired", extracted)
        self.assertTrue(extracted.endswith("expired.expires_at <= now())"))
        self.assertNotIn('"', extracted)
        self.assertNotIn('+', extracted)

    def test_dynamic_expressions_fail_instead_of_silently_omitting_visibility_filters(self):
        with self.assertRaises(ValueError):
            module.java_string_constant('String SQL = " WHERE " + visibilityFilter;', 'SQL')


if __name__ == '__main__':
    unittest.main()
