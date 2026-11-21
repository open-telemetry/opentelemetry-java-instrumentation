/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.invoke
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.base.TestSuite
import org.gradle.testing.base.TestSuiteTarget
import org.gradle.process.CommandLineArgumentProvider
import org.junit.jupiter.api.Test as JunitTest

class TestSuiteExecutionsTest {
  private val project = ProjectBuilder.builder().build().also {
    it.pluginManager.apply("java-library")
    it.pluginManager.apply(TestSuiteExecutionsPlugin::class.java)
  }

  @JunitTest
  fun `unit tests helper rejects ordinary tests before registering or configuring the suite`() {
    for (javaPlugin in listOf("java", "java-library")) {
      for (withExecutionsPlugin in listOf(false, true)) {
        val plainProject = ProjectBuilder.builder().build()
        plainProject.pluginManager.apply(javaPlugin)
        if (withExecutionsPlugin) {
          plainProject.pluginManager.apply(TestSuiteExecutionsPlugin::class.java)
        }
        val suites = plainProject.extensions.getByType(TestingExtension::class.java).suites

        assertThatThrownBy { suites.unitTests() }
          .isInstanceOf(IllegalArgumentException::class.java)
          .hasMessageContaining("unitTests() requires the Javaagent testing plugin")
          .hasMessageContaining("io.opentelemetry.instrumentation.javaagent-testing")
          .hasMessageContaining("Use src/test")
        var configured = false
        assertThatThrownBy { suites.unitTests { configured = true } }
          .isInstanceOf(IllegalArgumentException::class.java)
          .hasMessageContaining("Use src/test")
        assertThatThrownBy { suites.unitTests("namedUnitTests") { configured = true } }
          .isInstanceOf(IllegalArgumentException::class.java)
          .hasMessageContaining("Use src/test")

        assertThat(configured).isFalse()
        assertThat(suites.names).doesNotContain("unitTests", "namedUnitTests")
        assertThat(plainProject.tasks.names).doesNotContain("unitTests", "compileUnitTestsJava", "namedUnitTests")
      }
    }
  }

  @JunitTest
  fun `named unit tests coexist and isolate dependencies and variants`() {
    project.pluginManager.apply("io.opentelemetry.instrumentation.javaagent-testing")
    suites().register("optInTest", JvmTestSuite::class.java)
    val baseline = suites().unitTests {
      dependencies { implementation.add("example:library:1.0") }
    }
    val named = suites().unitTests("customUNITTESTS") {
      dependencies { implementation.add("example:library:2.0") }
      executions { register("Variant") {} }
    }
    (project as ProjectInternal).evaluate()

    for ((suite, version) in listOf(baseline to "1.0", named to "2.0")) {
      assertThat(
        project.configurations.getByName(suite.sources.implementationConfigurationName)
          .dependencies.filterIsInstance<ProjectDependency>().map { it.path }
      ).containsExactly(project.path)
      assertThat(
        project.configurations.getByName(suite.sources.compileClasspathConfigurationName)
          .allDependencies.filterIsInstance<ExternalModuleDependency>()
          .filter { it.group == "example" }.map { it.version }
      ).containsExactly(version)
      assertBinding(test(suite.name), suite)
    }
    for (taskName in listOf("customUNITTESTS", "customUNITTESTSVariant")) {
      val task = test(taskName)
      assertBinding(task, named)
      assertThat(task.jvmArgumentProviders).isEmpty()
      assertThat(task.jvmArgs).noneMatch { it.startsWith("-javaagent:") }
    }
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
      .contains("unitTests", "customUNITTESTS", "customUNITTESTSVariant")
      .doesNotContain("optInTest", "unitTestsVariant")
    assertThat(project.tasks.names).doesNotContain("unitTestsVariant")
  }

  @JunitTest
  fun `named unit tests reject invalid names before registering or configuring`() {
    project.pluginManager.apply("io.opentelemetry.instrumentation.javaagent-testing")
    var configured = false
    assertThatThrownBy { suites().unitTests("integrationTest") { configured = true } }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("must end in unitTests")
    assertThat(configured).isFalse()
    assertThat(suites().names).doesNotContain("integrationTest")
    assertThat(project.tasks.names).doesNotContain("integrationTest", "compileIntegrationTestJava")
  }

  @JunitTest
  fun `unit tests helper includes only its suite in check and keeps test tasks lazy`() {
    project.pluginManager.apply("io.opentelemetry.instrumentation.javaagent-testing")
    val configuredTasks = mutableListOf<String>()
    project.tasks.withType(Test::class.java).configureEach {
      configuredTasks.add(name)
    }
    suites().register("optInTest", JvmTestSuite::class.java)
    suites().invoke {
      val unitTests = unitTests()
      assertThat(unitTests.name).isEqualTo("unitTests")
    }
    val unitTests = suites().named("unitTests", JvmTestSuite::class.java)

    assertThat(configuredTasks).isEmpty()
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
      .contains("test", "unitTests")
      .doesNotContain("optInTest")
    assertThat(configuredTasks).doesNotContain("optInTest")
    assertBinding(test("unitTests"), unitTests.get())
    assertThat(
      project.configurations.getByName(unitTests.get().sources.implementationConfigurationName)
        .dependencies.filterIsInstance<ProjectDependency>()
        .map { it.path }
    ).containsExactly(project.path)
  }

  @JunitTest
  fun `unit tests helper keeps dependencies separate from integration tests`() {
    project.pluginManager.apply("io.opentelemetry.instrumentation.javaagent-testing")
    project.dependencies.add("testImplementation", "example:library:1.0")
    project.dependencies.add("testImplementation", "example:integration-only:1.0")
    val unitTests = suites().unitTests {
      dependencies {
        implementation.add("example:library:2.0")
      }
    }

    assertThat(
      project.configurations.getByName(unitTests.sources.compileClasspathConfigurationName)
        .allDependencies.filterIsInstance<ExternalModuleDependency>()
        .filter { it.group == "example" }
        .map { "${it.name}:${it.version}" }
    ).containsExactly("library:2.0")
  }

  @JunitTest
  fun `unit tests and their variants keep the project classpath and run without the agent`() {
    project.pluginManager.apply("io.opentelemetry.instrumentation.javaagent-testing")
    val unitTests = suites().unitTests {
      executions {
        register("Variant") {}
      }
    }
    (project as ProjectInternal).evaluate()

    for (taskName in listOf("unitTests", "unitTestsVariant")) {
      val task = test(taskName)
      assertBinding(task, unitTests)
      assertThat(task.jvmArgumentProviders).isEmpty()
      assertThat(task.jvmArgs).noneMatch { it.startsWith("-javaagent:") }
    }
  }

  @JunitTest
  fun `suite lookup identifies owners without guessing from task names`() {
    project.pluginManager.apply("io.opentelemetry.instrumentation.javaagent-testing")
    val owners = mutableMapOf<String, String?>()
    project.tasks.withType(Test::class.java).configureEach {
      owners[name] = project.plugins.getPlugin(TestSuiteExecutionsPlugin::class.java).suiteFor(this)?.name
    }
    suite("test").executions {
      register("UnitTests") {}
      register("Experimental") {}
    }
    suites().unitTests().executions {
      register("Experimental") {}
    }
    suites().register("legacyUNITTESTS", JvmTestSuite::class.java).get().executions {
      register("Experimental") {}
    }
    project.tasks.register("standaloneUnitTests", Test::class.java)
    project.tasks.register("unitTestsStandalone", Test::class.java)

    listOf(
      "test",
      "testUnitTests",
      "testExperimental",
      "unitTests",
      "unitTestsExperimental",
      "legacyUNITTESTS",
      "legacyUNITTESTSExperimental",
      "standaloneUnitTests",
      "unitTestsStandalone"
    ).forEach { test(it) }

    assertThat(owners).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "test" to "test",
        "testUnitTests" to "test",
        "testExperimental" to "test",
        "unitTests" to "unitTests",
        "unitTestsExperimental" to "unitTests",
        "legacyUNITTESTS" to "legacyUNITTESTS",
        "legacyUNITTESTSExperimental" to "legacyUNITTESTS",
        "standaloneUnitTests" to null,
        "unitTestsStandalone" to null
      )
    )
  }

  @JunitTest
  fun `variant owner is available when tasks are eagerly configured`() {
    project.pluginManager.apply("io.opentelemetry.instrumentation.javaagent-testing")
    val owners = mutableMapOf<String, String?>()
    project.tasks.withType(Test::class.java).all {
      owners[name] = project.plugins.getPlugin(TestSuiteExecutionsPlugin::class.java).suiteFor(this)?.name
    }
    suites().unitTests().executions {
      register("Variant") {}
    }

    assertThat(owners).containsEntry("unitTests", "unitTests").containsEntry("unitTestsVariant", "unitTests")
  }

  @JunitTest
  fun `bind default and named suites without new compilations or default tasks`() {
    val default = suite("test")
    val defaultTask = test("test")
    val named = suites().register("integrationTest", JvmTestSuite::class.java).get()
    val sources = project.extensions.getByType(SourceSetContainer::class.java).names.toSet()

    default.executions {
      register("Experimental") {}
    }
    named.executions {
      register("PreviewSemconv") {}
    }

    assertThat(test("test")).isSameAs(defaultTask)
    assertBinding(test("test"), default)
    assertBinding(test("testExperimental"), default)
    assertBinding(test("integrationTest"), named)
    assertBinding(test("integrationTestPreviewSemconv"), named)
    assertThat(project.extensions.getByType(SourceSetContainer::class.java).names).isEqualTo(sources)
    assertThat(project.tasks.withType(Test::class.java).names).containsExactlyInAnyOrder(
      "test", "testExperimental", "integrationTest", "integrationTestPreviewSemconv"
    )
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
      .contains("test", "testExperimental", "integrationTest", "integrationTestPreviewSemconv")
    assertThat(project.tasks.names).doesNotContain("testPreviewSemconv", "integrationTestExperimental")
  }

  @JunitTest
  fun `collection selector configures only the explicitly named suites`() {
    val integration = suites().register("integrationTest", JvmTestSuite::class.java).get()
    val untouched = suites().register("untouched", JvmTestSuite::class.java).get()

    suites().executions("test", integration.name) {
      configureEach {
        systemProperty("selected", "true")
      }
      register("Variant") {}
    }

    assertThat(test("test").systemProperties).containsEntry("selected", "true")
    assertThat(test("testVariant").systemProperties).containsEntry("selected", "true")
    assertThat(test("integrationTest").systemProperties).containsEntry("selected", "true")
    assertThat(test("integrationTestVariant").systemProperties).containsEntry("selected", "true")
    assertThat(test(untouched.name).systemProperties).doesNotContainKey("selected")
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
      .contains("testVariant", "integrationTestVariant")
      .doesNotContain("untouchedVariant")
  }

  @JunitTest
  fun `collection selector lazily configures suites registered later`() {
    suites().executions {
      configureEach {
        systemProperty("selected", "true")
      }
      register("Variant") {}
    }

    suites().register("lateTest", JvmTestSuite::class.java)

    assertThat(test("test").systemProperties).containsEntry("selected", "true")
    assertThat(test("lateTest").systemProperties).containsEntry("selected", "true")
    assertThat(test("lateTestVariant").systemProperties).containsEntry("selected", "true")
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
      .contains("lateTest", "lateTestVariant")
  }

  @JunitTest
  fun `collection selector rejects missing and non JVM suites`() {
    assertThatThrownBy { suites().executions("missing") {} }
      .hasMessageContaining("missing")

    suites().add(NonJvmTestSuite(project, "nonJvm"))
    assertThatThrownBy { suites().executions("nonJvm") {} }
      .hasMessageContaining("Test suite 'nonJvm' is not a JVM test suite")
  }

  @JunitTest
  fun `common configuration precedes variants regardless of declaration order`() {
    for (commonFirst in listOf(true, false)) {
      val suite = suites().register("suite$commonFirst", JvmTestSuite::class.java).get()
      suite.executions {
        if (commonFirst) {
          configureEach {
            jvmArgs("-Xss2m")
            systemProperty("setting", "common")
          }
        }
        register("Variant") {
          jvmArgs("-XX:+HeapDumpOnOutOfMemoryError")
          systemProperty("setting", "variant")
          filter.includeTestsMatching("example.*")
          enabled = false
        }
        if (!commonFirst) {
          configureEach {
            jvmArgs("-Xss2m")
            systemProperty("setting", "common")
          }
        }
      }

      val default = test(suite.name)
      val variant = test(suite.name + "Variant")
      assertThat(default.jvmArgs).containsExactly("-Xss2m")
      assertThat(default.systemProperties).containsEntry("setting", "common")
      assertThat(default.enabled).isTrue()
      assertThat(variant.jvmArgs).containsExactly("-Xss2m", "-XX:+HeapDumpOnOutOfMemoryError")
      assertThat(variant.systemProperties).containsEntry("setting", "variant")
      assertThat(variant.filter.includePatterns).containsExactly("example.*")
      assertThat(variant.enabled).isFalse()
    }
  }

  @JunitTest
  fun `default configuration follows common configuration and stays off variants`() {
    for (defaultFirst in listOf(true, false)) {
      val suiteName = if (defaultFirst) "defaultFirst" else "commonFirst"
      val testSuite = suites().register(suiteName, JvmTestSuite::class.java).get()
      testSuite.executions {
        if (defaultFirst) {
          configureDefault {
            systemProperty("setting", "default")
            systemProperty("default.only", "true")
            jvmArgs("-XX:+UseSerialGC")
            filter.excludeTestsMatching("baseline.*")
          }
        }
        configureEach {
          systemProperty("setting", "common")
          jvmArgs("-Xss2m")
        }
        if (!defaultFirst) {
          configureDefault {
            systemProperty("setting", "default")
            systemProperty("default.only", "true")
            jvmArgs("-XX:+UseSerialGC")
            filter.excludeTestsMatching("baseline.*")
          }
        }
        register("Variant") {}
      }

      val default = test(testSuite.name)
      val variant = test(testSuite.name + "Variant")
      assertThat(default.systemProperties)
        .containsEntry("setting", "default")
        .containsEntry("default.only", "true")
      assertThat(default.jvmArgs).containsExactly("-Xss2m", "-XX:+UseSerialGC")
      assertThat(default.filter.excludePatterns).containsExactly("baseline.*")
      assertThat(variant.systemProperties)
        .containsEntry("setting", "common")
        .doesNotContainKey("default.only")
      assertThat(variant.jvmArgs).containsExactly("-Xss2m")
      assertThat(variant.filter.excludePatterns).isEmpty()
      assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
        .contains(testSuite.name, testSuite.name + "Variant")
        .doesNotContain(testSuite.name + "Baseline")
    }
  }

  @JunitTest
  fun `default configuration is isolated to its named suite`() {
    suite("test").executions {
      configureDefault {
        systemProperty("suite.default", "test")
      }
      register("Variant") {}
    }
    val integrationSuite = suites().register("integrationTest", JvmTestSuite::class.java).get()
    integrationSuite.executions {
      configureDefault {
        systemProperty("suite.default", "integration")
      }
      register("Variant") {}
    }

    assertThat(test("test").systemProperties).containsEntry("suite.default", "test")
    assertThat(test("testVariant").systemProperties).doesNotContainKey("suite.default")
    assertThat(test("integrationTest").systemProperties).containsEntry("suite.default", "integration")
    assertThat(test("integrationTestVariant").systemProperties).doesNotContainKey("suite.default")
  }

  @JunitTest
  fun `telemetry settings compose and override without labeling ordinary properties`() {
    suite("test").executions {
      register("Variant") {
        telemetryConfig("otel.common", "override")
        telemetryConfig("otel.variant", "true")
      }
      configureEach {
        systemProperty("testLatestDeps", true)
        jvmArgs("-Djava.net.preferIPv4Stack=true")
        telemetryConfig("otel.common", "base")
        telemetryConfig("otel.shared", "true")
      }
    }

    assertThat(test("test").systemProperties)
      .containsEntry("metadataConfig", "otel.common=base,otel.shared=true")
      .containsEntry("otel.common", "base")
      .containsEntry("otel.shared", "true")
      .doesNotContainKey("otel.variant")
    assertThat(test("testVariant").systemProperties)
      .containsEntry("metadataConfig", "otel.common=override,otel.shared=true,otel.variant=true")
      .containsEntry("otel.common", "override")
      .containsEntry("otel.shared", "true")
      .containsEntry("otel.variant", "true")
      .containsEntry("testLatestDeps", true)
    assertThat(test("testVariant").allJvmArgs).containsOnlyOnce("-Dotel.common=override")
    assertThat(test("testVariant").allJvmArgs).doesNotContain("-Dotel.common=base")
  }

  @JunitTest
  fun `default metadata stays unset and unconfigured suites stay untouched`() {
    val untouched = suites().register("untouched", JvmTestSuite::class.java).get()
    suite("test").executions {
      configureEach {
        systemProperty("ordinary", "true")
      }
      register("Variant") {
        telemetryConfig("otel.variant", "true")
      }
    }

    assertThat(test("test").systemProperties).doesNotContainKey("metadataConfig")
    assertThat(test(untouched.name).systemProperties).doesNotContainKeys("ordinary", "metadataConfig")
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
      .doesNotContain(untouched.name)
  }

  @JunitTest
  fun `variants stay lazy and receive shared providers independently without flattening`() {
    var configuredTasks = 0
    var providerReads = 0
    project.tasks.withType(Test::class.java).configureEach {
      configuredTasks++
      jvmArgumentProviders.add(CommandLineArgumentProvider {
        providerReads++
        listOf("-javaagent:agent.jar")
      })
    }
    val default = suite("test")
    default.executions {
      configureEach {
        jvmArgs("-Xss2m")
      }
      register("Variant") {}
    }
    assertThat(configuredTasks).isZero()
    assertThat(providerReads).isZero()

    val variant = test("testVariant")
    assertThat(configuredTasks).isEqualTo(1)
    assertThat(providerReads).isZero()
    assertThat(variant.jvmArgs).containsExactly("-Xss2m")
    assertThat(variant.jvmArgumentProviders).hasSize(1)
    assertThat(variant.allJvmArgs).containsOnlyOnce("-javaagent:agent.jar")
    assertThat(configuredTasks).isEqualTo(1)
    assertThat(test("test").jvmArgumentProviders).hasSize(1)
    assertThat(test("test").jvmArgumentProviders.single()).isNotSameAs(variant.jvmArgumentProviders.single())
    assertThat(test("test").allJvmArgs).containsOnlyOnce("-javaagent:agent.jar")
  }

  @JunitTest
  fun `common configuration also applies to an already realized default`() {
    val default = test("test")
    suite("test").executions {
      register("Variant") {}
      configureEach {
        systemProperty("common", "true")
      }
      configureDefault {
        systemProperty("default", "true")
      }
    }
    assertThat(default.systemProperties).containsEntry("common", "true")
    assertThat(default.systemProperties).containsEntry("default", "true")
    assertThat(test("testVariant").systemProperties).containsEntry("common", "true")
    assertThat(test("testVariant").systemProperties).doesNotContainKey("default")
  }

  @JunitTest
  fun `default configuration remains lazy until its task is realized`() {
    var configurationCount = 0
    suite("test").executions {
      configureDefault {
        configurationCount++
        systemProperty("default", "true")
      }
      register("Variant") {}
    }

    assertThat(configurationCount).isZero()
    assertThat(test("testVariant").systemProperties).doesNotContainKey("default")
    assertThat(configurationCount).isZero()
    assertThat(test("test").systemProperties).containsEntry("default", "true")
    assertThat(configurationCount).isEqualTo(1)
  }

  @JunitTest
  fun `reject duplicate blocks and empty or duplicate suffixes`() {
    suite("test").executions {}
    assertThatThrownBy { suite("test").executions {} }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Declare one executions block per suite")
    assertThatThrownBy {
      suites().register("empty", JvmTestSuite::class.java).get().executions {
        register("") {}
      }
    }.isInstanceOf(IllegalArgumentException::class.java)
    assertThatThrownBy {
      suites().register("duplicate", JvmTestSuite::class.java).get().executions {
        register("Variant") {}
        register("Variant") {}
      }
    }.isInstanceOf(IllegalArgumentException::class.java)
  }

  private fun suites() = project.extensions.getByType(TestingExtension::class.java).suites

  private fun suite(name: String) = suites().getByName(name) as JvmTestSuite

  private fun test(name: String) = project.tasks.getByName(name) as Test

  private fun assertBinding(task: Test, suite: JvmTestSuite) {
    assertThat(task.testClassesDirs.files).isEqualTo(suite.sources.output.classesDirs.files)
    // Inspect the file collection graph without resolving the suite's external test dependencies.
    assertThat(task.classpath).isSameAs(suite.sources.runtimeClasspath)
  }

  private class NonJvmTestSuite(private val project: Project, private val suiteName: String) : TestSuite {
    override fun getName() = suiteName

    override fun getTargets(): DomainObjectCollection<out TestSuiteTarget> =
      project.objects.domainObjectSet(TestSuiteTarget::class.java)
  }
}
