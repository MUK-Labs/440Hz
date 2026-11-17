"""Checks for publication selection; no network, credentials, or release mutations."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("publish_apk", Path(__file__).with_name("publish-apk.py"))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublishingChecks(unittest.TestCase):
    bundled = [{"version": "0.4.0", "versionCode": 4}, {"version": "0.3.0", "versionCode": 3}]

    def test_current_bundle_is_never_replaced_by_new_debug_key(self):
        self.assertIn("bundled", publisher.skip_reason("0.4.0", 4, self.bundled, []))

    def test_new_version_is_publishable(self):
        self.assertIsNone(publisher.skip_reason("0.5.0", 5, self.bundled, []))

    def test_old_build_cannot_promote_over_newer_release(self):
        releases = [{"tag_name": "v0.6.0", "draft": False, "prerelease": False}]
        self.assertIn("stale", publisher.skip_reason("0.5.0", 5, self.bundled, releases))

    def test_existing_draft_or_tag_is_not_overwritten(self):
        self.assertIn("exists", publisher.skip_reason("0.5.0", 5, self.bundled,
            [{"tag_name": "v0.5.0", "draft": True}]))
        self.assertIn("tag", publisher.skip_reason("0.5.0", 5, self.bundled, [], True))

    def test_version_code_must_increase(self):
        with self.assertRaises(ValueError):
            publisher.skip_reason("0.5.0", 4, self.bundled, [])

    def test_version_code_must_exceed_previously_published_release(self):
        releases = [{"tag_name": "v0.5.0", "draft": False, "prerelease": False,
                     "body": "Android test build **0.5.0** (version code 5).\n"}]
        with self.assertRaises(ValueError):
            publisher.skip_reason("0.6.0", 5, self.bundled, releases)
        self.assertIsNone(publisher.skip_reason("0.6.0", 6, self.bundled, releases))

    def test_version_parsing_is_numeric_not_lexical(self):
        self.assertGreater(publisher.version_tuple("0.10.0"), publisher.version_tuple("0.9.0"))
        with self.assertRaises(ValueError):
            publisher.version_tuple("1.0.0; command")

    def test_literal_source_version(self):
        self.assertEqual(publisher.source_version('    versionCode = 5\n    versionName = "0.5.0"\n'),
                         ("0.5.0", 5))
        with self.assertRaises(ValueError):
            publisher.source_version('versionCode = 5\nversionName = property("version")\n')


if __name__ == "__main__":
    unittest.main()
