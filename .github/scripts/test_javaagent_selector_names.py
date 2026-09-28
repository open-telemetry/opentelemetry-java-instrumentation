import contextlib
import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from check_javaagent_selector_names import check, main


class JavaagentSelectorNamesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def module(self, owner, filename, arguments, source_set="main"):
        path = self.root / owner / "javaagent" / "src" / source_set / "java" / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            "@AutoService(InstrumentationModule.class)\n"
            f"class {filename[:-5]} extends InstrumentationModule {{\n"
            f"  {filename[:-5]}() {{ super({arguments}); }}\n"
            "}\n",
            encoding="utf-8",
        )

    def test_single_versioned_module(self):
        self.module("http-client-5.0", "ClientModule.java", '"http-client", "http-client-5.0"')
        self.assertEqual(check(self.root), [])

    def test_shared_project_can_select_modules_together(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-client"',
        )
        self.module("http-client-5.0", "ServerModule.java", '"http-client", "http-client-5.0"')
        self.assertEqual(check(self.root), [])

    def test_component_can_have_own_version_after_owning_base(self):
        self.module(
            "jaxrs/jaxrs-2.0/jaxrs-2.0-jersey-3.0",
            "JerseyModule.java",
            '"jaxrs", "jaxrs-2.0", "jaxrs-2.0-jersey-3.0"',
        )
        self.assertEqual(check(self.root), [])

    def test_subgroup_and_umbrella_aliases_fail(self):
        self.module(
            "spring/spring-webflux-5.0",
            "WebfluxModule.java",
            '"spring-webflux", "spring-webflux-5.0", "spring-webflux-server"',
        )
        self.assertIn("expected exact selector", check(self.root)[0])

    def test_extra_alias_fails_even_after_exact_component(self):
        self.module(
            "akka/akka-http-10.0",
            "ServerModule.java",
            '"akka-http", "akka-http-10.0", "akka-http-10.0-server", "akka-http-server"',
        )
        self.assertIn("at most one", check(self.root)[0])

    def test_preview_only_names_ignore_legacy_branch(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"http-client", AgentCommonConfig.get().isV3Preview() '
            '? new String[] {"http-client-5.0"} '
            ': new String[] {"http-client-5.0", "http-client-legacy"}',
        )
        self.assertEqual(check(self.root), [])

    def test_explicit_deprecated_alias_dropped_in_preview(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"http-client", expandDeprecatedNames('
            '"http-client-5.0|deprecated:http-client-old", "http-client-5.0-client")',
        )
        self.assertEqual(check(self.root), [])

    def test_preview_wrong_base_fails(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"http-client", AgentCommonConfig.get().isV3Preview() '
            '? new String[] {"http-client-6.0"} : new String[] {"http-client-5.0"}',
        )
        self.assertIn("expected first selectors", check(self.root)[0])

    def test_preview_main_name_checked_in_first_configured_position(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            'AgentCommonConfig.get().isV3Preview() ? "http-client" : "legacy-client", '
            '"http-client-5.0"',
        )
        self.assertEqual(check(self.root), [])
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"legacy-client", "http-client", "http-client-5.0"',
        )
        self.assertIn("expected first selectors", check(self.root)[0])

    def test_repeated_selector_is_deduplicated_by_runtime(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"http-client", expandDeprecatedNames("http-client-5.0", "http-client")',
        )
        self.assertEqual(check(self.root), [])

    def test_jdk_versionless_modules_and_components(self):
        self.module("jdbc", "JdbcModule.java", '"jdbc"')
        self.module("jdbc", "DataSourceModule.java", '"jdbc", "jdbc-datasource"')
        self.assertEqual(check(self.root), [])

    def test_unknown_versionless_library_fails(self):
        self.module("library", "LibraryModule.java", '"library"')
        self.assertIn("not a JDK instrumentation", check(self.root)[0])

    def test_abstract_module_passes_selector_from_concrete_subclass(self):
        owner = "http-client-5.0"
        self.module(owner, "ClientModule.java", '"http-client-5.0-client"')
        concrete = self.root / owner / "javaagent" / "src" / "main" / "java" / "ClientModule.java"
        concrete.write_text(
            "@AutoService(InstrumentationModule.class)\n"
            "class ClientModule extends AbstractClientModule {\n"
            '  ClientModule() { super("http-client-5.0-client"); }\n'
            "}\n",
            encoding="utf-8",
        )
        concrete.with_name("AbstractClientModule.java").write_text(
            "abstract class AbstractClientModule extends InstrumentationModule {\n"
            "  AbstractClientModule(String component) {\n"
            '    super("http-client", "http-client-5.0", component);\n'
            "  }\n"
            "}\n",
            encoding="utf-8",
        )
        self.assertEqual(check(self.root), [])

    def test_unsupported_inherited_module_reports_error(self):
        self.module("http-client-5.0", "ClientModule.java", '"client"')
        path = (
            self.root
            / "http-client-5.0"
            / "javaagent"
            / "src"
            / "main"
            / "java"
            / "ClientModule.java"
        )
        path.write_text(
            "@AutoService(InstrumentationModule.class)\n"
            'class ClientModule extends UnknownModule { ClientModule() { super("client"); } }\n',
            encoding="utf-8",
        )
        self.assertIn("unsupported InstrumentationModule superclass", check(self.root)[0])

    def test_test_and_internal_modules_are_excluded(self):
        self.module("http-client-5.0", "TestModule.java", '"incorrect"', source_set="test")
        self.module("internal/internal-reflection", "ReflectionModule.java", '"incorrect"')
        self.module("external-annotations", "ExternalModule.java", '"incorrect"')
        self.assertEqual(check(self.root), [])

    def test_unrecognized_expression_is_an_error(self):
        self.module("http-client-5.0", "ClientModule.java", '"http-client", names()')
        self.assertIn("unsupported selector expression", check(self.root)[0])

    def test_missing_instrumentation_root_fails(self):
        with patch.object(sys, "argv", ["checker", str(self.root / "missing")]):
            with contextlib.redirect_stderr(io.StringIO()) as stderr:
                self.assertEqual(main(), 1)
        self.assertIn("No javaagent InstrumentationModule sources", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
