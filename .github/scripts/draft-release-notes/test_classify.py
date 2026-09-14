import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).with_name("classify.py")
SPEC = importlib.util.spec_from_file_location("draft_release_notes_classify", MODULE_PATH)
classify = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = classify
SPEC.loader.exec_module(classify)


class PreclassifyTest(unittest.TestCase):
    def test_does_not_omit_runtime_prs_with_hand_written_changelog_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={
                    "files": [
                        {"path": "CHANGELOG.md"},
                        {"path": "instrumentation/example/src/main/java/Example.java"},
                    ],
                    "labels": [],
                    "touches_src_main": True,
                },
                diff="",
            )

            result = classify.preclassify(bundle)

        self.assertIsNone(result)

    def test_changed_paths_accepts_git_fallback_strings(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={"files": ["CHANGELOG.md"]},
                diff="",
            )

            self.assertEqual(classify.changed_paths(bundle), ["CHANGELOG.md"])

    def test_build_prompt_accepts_git_fallback_strings(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={"files": ["instrumentation/example/src/main/java/Example.java"]},
                diff="+runtime change\n",
            )

            prompt = classify.build_prompt(bundle, "rules")

        self.assertIn(
            "  - instrumentation/example/src/main/java/Example.java",
            prompt,
        )

    def test_release_context_detects_rewrite_of_new_api(self):
        diff = """- \t+++  NEW METHOD: PUBLIC java.util.Collection names()
+ \t+++  NEW METHOD: PUBLIC java.lang.Iterable names()
"""

        self.assertIn("both the old and new signatures as NEW", classify.release_context_for(diff))

    def test_deprecation_validation_rejects_extra_compatibility_commentary(self):
        decision = {
            "decision": "include",
            "section": "deprecations",
            "bullet": (
                "Deprecate `old` in favor of `new`. "
                "The deprecated property still matches exact names."
            ),
            "evidence": "diff evidence",
        }

        self.assertIn(
            'deprecation bullet must omit "the deprecated "',
            classify.validate(decision),
        )

    def test_deprecation_validation_rejects_declarative_config_alternative(self):
        decision = {
            "decision": "include",
            "section": "deprecations",
            "bullet": (
                "Deprecate `old` in favor of `new` or equivalent declarative "
                "instrumentation configuration."
            ),
            "evidence": "diff evidence",
        }

        self.assertIn(
            'deprecation bullet must omit "declarative instrumentation configuration"',
            classify.validate(decision),
        )

    def test_added_runtime_deprecation_notice_is_detected(self):
        self.assertTrue(
            classify.adds_deprecation_notice(
                "diff --git a/src/main/java/Example.java b/src/main/java/Example.java\n"
                "+logger.warn(\"old.property is deprecated\");\n"
            )
        )

    def test_release_context_calls_out_possible_deprecation(self):
        diff = (
            "diff --git a/src/main/java/Example.java b/src/main/java/Example.java\n"
            "+@Deprecated\n"
        )

        self.assertIn("adds a deprecation declaration", classify.release_context_for(diff))

    def test_deprecated_experimental_method_must_be_named(self):
        diff = """diff --git a/metadata.yaml b/metadata.yaml
     otel.instrumentation.example.old:
       name: otel.instrumentation.example.old
       description: |
+        Deprecated: use `otel.instrumentation.example.new` instead.
diff --git a/module/src/main/java/example/internal/Experimental.java b/module/src/main/java/example/internal/Experimental.java
+   * @deprecated Use replacement().
+   */
+  @Deprecated
   public static void oldMethod(Builder builder, boolean enabled) {
"""
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={},
                diff=diff,
            )
            decision = {
                "decision": "include",
                "section": "deprecations",
                "bullet": "Deprecate `old.property` in favor of `new.property`.",
                "evidence": "diff evidence",
            }

            self.assertIn(
                "deprecation bullet must name `oldMethod(...)`",
                classify.validate(decision, bundle),
            )
            self.assertIn(
                "deprecation bullet must name `otel.instrumentation.example.old`",
                classify.validate(decision, bundle),
            )
            self.assertIn(
                "deprecation bullet must name `otel.instrumentation.example.new`",
                classify.validate(decision, bundle),
            )

    def test_changelog_deprecation_does_not_drive_classification(self):
        self.assertFalse(
            classify.adds_deprecation_notice(
                "diff --git a/CHANGELOG.md b/CHANGELOG.md\n"
                "+- Deprecate `old.property` in favor of `new.property`.\n"
            )
        )

    def test_changelog_diff_is_removed_from_prompt_input(self):
        diff = """commit message
diff --git a/CHANGELOG.md b/CHANGELOG.md
+hand-written entry
diff --git a/src/main/java/Example.java b/src/main/java/Example.java
+runtime change
"""

        result = classify.strip_changelog_diff(diff)

        self.assertNotIn("hand-written entry", result)
        self.assertIn("runtime change", result)

    def test_commit_metadata_is_removed_from_prompt_input(self):
        diff = """commit abc
Author: Example <example@example.com>

Commit subject

diff --git a/src/main/java/Example.java b/src/main/java/Example.java
+runtime change
"""

        result = classify.strip_changelog_diff(diff)

        self.assertNotIn("Commit subject", result)
        self.assertNotIn("Author:", result)
        self.assertIn("runtime change", result)

    def test_compact_diff_keeps_deprecation_near_end(self):
        diff = (
            "diff --git a/src/main/java/Example.java b/src/main/java/Example.java\n"
            + (" context\n" * 100)
            + "+ * @deprecated Use replacement().\n"
            + "+ @Deprecated\n"
            + " public void oldMethod() {}\n"
        )

        result = classify.compact_diff(diff, 500)

        self.assertLessEqual(len(result), 500)
        self.assertIn("@Deprecated", result)
        self.assertIn("oldMethod", result)

    def test_build_prompt_stays_below_windows_command_line_limit(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={
                    "files": [
                        {
                            "path": "instrumentation/example/src/main/java/"
                            + ("VeryLongDirectory/" * 5)
                            + f"Example{index}.java",
                            "additions": 1,
                            "deletions": 0,
                        }
                        for index in range(50)
                    ]
                },
                diff="+runtime change\n" * 5_000,
            )

            prompt = classify.build_prompt(bundle, "rules")

        self.assertLessEqual(
            classify.utf16_units(prompt),
            classify.MAX_PROMPT_UTF16_UNITS,
        )

    def test_build_prompt_counts_supplementary_unicode_as_two_utf16_units(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={"files": []},
                diff="+😀\n" * 20_000,
            )

            prompt = classify.build_prompt(bundle, "rules")

        self.assertLessEqual(
            classify.utf16_units(prompt),
            classify.MAX_PROMPT_UTF16_UNITS,
        )

    def test_classifier_fingerprint_changes_with_rules(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={"commit_hash": "abc"},
                diff="+runtime change\n",
            )

            first = classify.classifier_fingerprint(bundle, "first rules")
            second = classify.classifier_fingerprint(bundle, "second rules")

        self.assertNotEqual(first, second)

    def test_classifier_fingerprint_changes_with_model(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = classify.PrBundle(
                pr=123,
                dir=Path(directory),
                meta={"commit_hash": "abc"},
                diff="+runtime change\n",
            )

            with mock.patch.dict("os.environ", {"CLASSIFY_MODEL": "model-a"}):
                first = classify.classifier_fingerprint(bundle, "rules")
            with mock.patch.dict("os.environ", {"CLASSIFY_MODEL": "model-b"}):
                second = classify.classifier_fingerprint(bundle, "rules")

        self.assertNotEqual(first, second)

    def test_validation_rejects_non_string_fields(self):
        decision = {
            "decision": "include",
            "section": "deprecations",
            "surface": [],
            "user_visible_effect": {},
            "bullet": ["not", "a", "string"],
            "evidence": ["not", "a", "string"],
        }

        errors = classify.validate(decision)

        self.assertIn("surface must be a string", errors)
        self.assertIn("user_visible_effect must be a string", errors)
        self.assertIn("bullet required for include", errors)
        self.assertIn("evidence required", errors)

    def test_validation_rejects_embedded_pr_link(self):
        decision = {
            "decision": "include",
            "section": "enhancements",
            "surface": "example",
            "user_visible_effect": "example",
            "bullet": (
                "Add an example.\n"
                "  ([#123](https://github.com/open-telemetry/"
                "opentelemetry-java-instrumentation/pull/123))"
            ),
            "evidence": "diff evidence",
        }

        errors = classify.validate(decision)

        self.assertIn("bullet must be a single line without a PR link", errors)
        self.assertIn("bullet must not include a PR link", errors)


if __name__ == "__main__":
    unittest.main()
