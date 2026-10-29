/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.base.TestingExtension
import org.gradle.process.CommandLineArgumentProvider
import org.junit.jupiter.api.Test as JunitTest

class TestSuiteExecutionsTest {
  private val project = ProjectBuilder.builder().build().also {
    it.pluginManager.apply("java-library")
    it.pluginManager.apply(TestSuiteExecutionsPlugin::class.java)
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
  fun `HTTP baggage executions are filtered and excluded from metadata`() {
    val testSuite = suite("test")
    testSuite.executions {
      register("Existing") {}
    }
    suites().register("integrationTest", JvmTestSuite::class.java)
    project.extensions.getByType(TestingExtension::class.java)
      .registerHttpServerBaggageTestExecutions(listOf("test"))

    val baggageTask = test("testBaggageExtraction")
    assertBinding(baggageTask, testSuite)
    assertThat(baggageTask.filter.includePatterns)
      .containsExactlyInAnyOrder("*.extractSingleBaggage", "*.extractMultiBaggage")
    assertThat(baggageTask.systemProperties)
      .containsEntry(
        "otel.java.experimental.span-attributes.copy-from-baggage.include",
        "test-baggage-key-1,test-baggage-key-2"
      )
      .containsEntry("collectMetadata", false)
      .doesNotContainKey("metadataConfig")
    assertThat(
        project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name }
      )
      .contains("testBaggageExtraction")
    assertThat(project.tasks.names).doesNotContain("integrationTestBaggageExtraction")
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
    }
    assertThat(default.systemProperties).containsEntry("common", "true")
    assertThat(test("testVariant").systemProperties).containsEntry("common", "true")
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
}
