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

    def module(
        self,
        owner,
        filename,
        arguments,
        source_set="main",
        annotation="@AutoService(InstrumentationModule.class)",
    ):
        path = self.root / owner / "javaagent" / "src" / source_set / "java" / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            f"{annotation}\n"
            f"class {filename[:-5]} extends InstrumentationModule {{\n"
            f"  {filename[:-5]}() {{ super({arguments}); }}\n"
            "}\n",
            encoding="utf-8",
        )

    def test_single_versioned_module(self):
        self.module("http-client-5.0", "ClientModule.java", '"http-client", "http-client-5.0"')
        self.assertEqual(check(self.root), [])

    def test_shared_project_requires_exact_component_selectors(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-client"',
        )
        self.module(
            "http-client-5.0", "ServerModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-server"',
        )
        self.assertEqual(check(self.root), [])

    def test_shared_baseline_missing_exact_selector_fails(self):
        self.module("http-client-5.0", "CoreModule.java", '"http-client", "http-client-5.0"')
        self.module(
            "http-client-5.0", "ClientModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-client"',
        )
        self.assertIn("need a unique exact component selector", check(self.root)[0])

    def test_duplicate_exact_selectors_fail_for_both_modules(self):
        self.module(
            "http-client-5.0", "ClientModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-client"',
        )
        self.module(
            "http-client-5.0", "OtherClientModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-client"',
        )
        errors = check(self.root)
        self.assertEqual(len(errors), 2)
        self.assertTrue(all("is not unique" in error for error in errors))

    def test_nested_projects_share_the_owning_baseline(self):
        self.module(
            "jaxrs/jaxrs-2.0/jaxrs-2.0-cxf-3.2", "CxfModule.java",
            '"jaxrs", "jaxrs-2.0", "jaxrs-2.0-cxf-3.2"',
        )
        self.module(
            "jaxrs/jaxrs-2.0/jaxrs-2.0-jersey-2.0", "JerseyModule.java",
            '"jaxrs", "jaxrs-2.0"',
        )
        self.assertIn("need a unique exact component selector", check(self.root)[0])

    def test_identical_components_in_different_baselines_are_independent(self):
        self.module(
            "http-client-5.0", "CoreModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-core"',
        )
        self.module(
            "http-client-6.0", "CoreModule.java",
            '"http-client", "http-client-6.0", "http-client-6.0-core"',
        )
        self.module(
            "http-client-5.0", "ClientModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-client"',
        )
        self.module(
            "http-client-6.0", "ClientModule.java",
            '"http-client", "http-client-6.0", "http-client-6.0-client"',
        )
        self.assertEqual(check(self.root), [])

    def test_standalone_module_rejects_component_selector(self):
        self.module(
            "http-client-5.0", "ClientModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-client"',
        )
        self.assertIn("standalone modules must not register", check(self.root)[0])

    def test_component_can_have_own_version_after_owning_base(self):
        self.module(
            "jaxrs/jaxrs-2.0/jaxrs-2.0-annotations", "AnnotationsModule.java",
            '"jaxrs", "jaxrs-2.0", "jaxrs-2.0-annotations"',
        )
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
            "http-client-5.0", "CoreModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-core"',
        )
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
        self.module("jdbc", "JdbcModule.java", '"jdbc", "jdbc-core"')
        self.module("jdbc", "DataSourceModule.java", '"jdbc", "jdbc-datasource"')
        self.assertEqual(check(self.root), [])

    def test_multi_service_registration_is_checked(self):
        self.module(
            "executors",
            "ExecutorsModule.java",
            '"incorrect"',
            annotation="@AutoService({InstrumentationModule.class, EarlyInstrumentationModule.class})",
        )
        self.assertIn("expected first selectors", check(self.root)[0])

    def test_jdk_shared_family_requires_exact_component_selectors(self):
        self.module("jdbc", "JdbcModule.java", '"jdbc"')
        self.module("jdbc", "DataSourceModule.java", '"jdbc", "jdbc-datasource"')
        self.assertIn("need a unique exact component selector", check(self.root)[0])

    def test_main_reports_checked_module_count(self):
        self.module("http-client-5.0", "ClientModule.java", '"http-client", "http-client-5.0"')
        with patch.object(sys, "argv", ["checker", str(self.root)]):
            with contextlib.redirect_stdout(io.StringIO()) as stdout:
                self.assertEqual(main(), 0)
        self.assertIn("Checked 1 javaagent modules", stdout.getvalue())

    def test_empty_varargs_array_adds_no_selectors(self):
        self.module("jdbc", "JdbcModule.java", '"jdbc", "jdbc-core"')
        self.module(
            "jdbc",
            "DataSourceModule.java",
            'AgentCommonConfig.get().isV3Preview() ? "jdbc" : "jdbc-datasource", '
            'AgentCommonConfig.get().isV3Preview() '
            '? new String[] {"jdbc-datasource"} : new String[0]',
        )
        self.assertEqual(check(self.root), [])

    def test_empty_preview_array_misses_required_selectors(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"http-client", AgentCommonConfig.get().isV3Preview() '
            '? new String[] {} : new String[] {"http-client-5.0"}',
        )
        self.assertIn("expected first selectors", check(self.root)[0])

    def test_preview_branches_on_both_main_name_and_varargs(self):
        self.module(
            "jaxws/jaxws-2.0", "CoreModule.java", '"jaxws", "jaxws-2.0", "jaxws-2.0-core"',
        )
        self.module(
            "jaxws/jaxws-2.0-cxf-3.0",
            "CxfModule.java",
            'AgentCommonConfig.get().isV3Preview() ? "jaxws" : "cxf", '
            'AgentCommonConfig.get().isV3Preview() '
            '? new String[] {"jaxws-2.0", "jaxws-2.0-cxf-3.0"} '
            ': expandDeprecatedNames("jaxws-2.0-cxf-3.0|deprecated:jaxws-cxf-3.0", "jaxws")',
        )
        self.assertEqual(check(self.root), [])

    def test_preview_branch_keeps_deprecated_names_of_legacy_branch_out(self):
        self.module(
            "akka/akka-actor-forkjoin-2.5",
            "ForkJoinModule.java",
            '"akka-actor-forkjoin", AgentCommonConfig.get().isV3Preview() '
            '? new String[] {"akka-actor-forkjoin-2.5"} '
            ': expandDeprecatedNames('
            '"akka-actor-forkjoin|deprecated:akka-actor-fork-join", '
            '"akka-actor-forkjoin-2.5|deprecated:akka-actor-fork-join-2.5", "akka-actor")',
        )
        self.assertEqual(check(self.root), [])

    def test_component_may_carry_its_own_patch_version(self):
        self.module(
            "vertx/vertx-redis-client/vertx-redis-client-4.0", "CoreModule.java",
            '"vertx-redis-client", "vertx-redis-client-4.0", "vertx-redis-client-4.0-core"',
        )
        self.module(
            "vertx/vertx-redis-client/vertx-redis-client-4.0",
            "RedisModule.java",
            '"vertx-redis-client", "vertx-redis-client-4.0", '
            '"vertx-redis-client-4.0-core-4.4.5"',
        )
        self.assertEqual(check(self.root), [])

    def test_bare_version_is_not_a_component(self):
        self.module(
            "vertx/vertx-redis-client/vertx-redis-client-4.0",
            "RedisModule.java",
            '"vertx-redis-client", "vertx-redis-client-4.0", "vertx-redis-client-4.4.5"',
        )
        self.assertIn("expected exact selector", check(self.root)[0])

    def test_component_of_family_without_base_is_rejected(self):
        self.module(
            "kafka/kafka-clients/kafka-clients-0.11",
            "MetricsModule.java",
            '"kafka-clients", "kafka-clients-0.11", "kafka-clients-metrics"',
        )
        self.assertIn("expected exact selector", check(self.root)[0])

    def test_preview_fallback_superclass_is_supported(self):
        owner = "opentelemetry-api/opentelemetry-api-1.31"
        self.module(
            owner, "CoreModule.java",
            '"opentelemetry-api", "opentelemetry-api-1.31", "opentelemetry-api-1.31-core"',
        )
        path = self.root / owner / "javaagent" / "src" / "main" / "java" / "ApiModule.java"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            "@AutoService(InstrumentationModule.class)\n"
            "class ApiModule extends V3PreviewFallbackEnabledInstrumentationModule {\n"
            '  ApiModule() { super("opentelemetry-api", AgentCommonConfig.get().isV3Preview()\n'
            '      ? new String[] {"opentelemetry-api-1.31", "opentelemetry-api-1.31-incubator"}\n'
            '      : new String[] {"opentelemetry-api-1.31", '
            '"opentelemetry-api-incubator-1.31"}); }\n'
            "}\n",
            encoding="utf-8",
        )
        self.assertEqual(check(self.root), [])

    def test_abstract_preview_fallback_base_is_supported(self):
        owner = "http-client-5.0"
        self.module(
            owner, "CoreModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-core"',
        )
        concrete = self.root / owner / "javaagent" / "src" / "main" / "java" / "ClientModule.java"
        concrete.parent.mkdir(parents=True, exist_ok=True)
        concrete.write_text(
            "@AutoService(InstrumentationModule.class)\n"
            "class ClientModule extends AbstractClientModule {\n"
            '  ClientModule() { super("http-client-5.0-client"); }\n'
            "}\n",
            encoding="utf-8",
        )
        concrete.with_name("AbstractClientModule.java").write_text(
            "abstract class AbstractClientModule\n"
            "    extends V3PreviewFallbackEnabledInstrumentationModule {\n"
            "  AbstractClientModule(String component) {\n"
            '    super("http-client", "http-client-5.0", component);\n'
            "  }\n"
            "}\n",
            encoding="utf-8",
        )
        self.assertEqual(check(self.root), [])

    def test_comment_between_selectors_is_ignored(self):
        path = (
            self.root
            / "spring/spring-boot-actuator-autoconfigure-2.0"
            / "javaagent"
            / "src"
            / "main"
            / "java"
            / "ActuatorModule.java"
        )
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            "@AutoService(InstrumentationModule.class)\n"
            "class ActuatorModule extends InstrumentationModule {\n"
            "  ActuatorModule() {\n"
            '    super("spring-boot-actuator-autoconfigure",\n'
            "        AgentCommonConfig.get().isV3Preview()\n"
            '            ? new String[] {"spring-boot-actuator-autoconfigure-2.0"}\n'
            "            : new String[] {\n"
            '              "spring-boot-actuator-autoconfigure-2.0",\n'
            "              // shared with MicrometerInstrumentationModule\n"
            '              "micrometer"\n'
            "            });\n"
            "  }\n"
            "}\n",
            encoding="utf-8",
        )
        self.assertEqual(check(self.root), [])

    def test_unsupported_preview_condition_is_an_error(self):
        self.module(
            "http-client-5.0",
            "ClientModule.java",
            '"http-client", isV3Preview() '
            '? new String[] {"http-client-5.0"} : new String[] {"http-client-5.0"}',
        )
        self.assertIn("unsupported selector condition", check(self.root)[0])

    def test_unknown_versionless_library_fails(self):
        self.module("library", "LibraryModule.java", '"library"')
        self.assertIn("not a JDK instrumentation", check(self.root)[0])

    def test_abstract_module_passes_selector_from_concrete_subclass(self):
        owner = "http-client-5.0"
        self.module(
            owner, "CoreModule.java",
            '"http-client", "http-client-5.0", "http-client-5.0-core"',
        )
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
