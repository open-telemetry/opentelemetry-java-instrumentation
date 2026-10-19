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
  fun `mapped executions keep default profiles separate and retain source suite settings`() {
    val source = suites().register("clientTest", JvmTestSuite::class.java).get()
    val mappingKey = "otel.instrumentation.common.peer-service-mapping"
    val mapping = "127.0.0.1=test-peer-service,localhost=test-peer-service,192.0.2.1=test-peer-service"
    source.executions {
      configureEach {
        systemProperty("testLatestDeps", true)
        jvmArgs("-Djava.net.preferIPv4Stack=true")
        filter.includeTestsMatching("*ClientTest")
        exclude("**/server/**")
      }
      register("WithPeerServiceMapping") {
        telemetryConfig(mappingKey, mapping)
      }
      register("PreviewSemconvWithPeerServiceMapping") {
        telemetryConfig("otel.semconv-stability.preview", "service.peer")
        telemetryConfig(mappingKey, mapping)
      }
    }

    assertThat(test(source.name).systemProperties)
      .doesNotContainKeys(mappingKey, "otel.semconv-stability.preview", "metadataConfig")
    assertThat(test(source.name + "WithPeerServiceMapping").systemProperties)
      .containsEntry(mappingKey, mapping)
      .containsEntry("metadataConfig", "$mappingKey=$mapping")
      .doesNotContainKey("otel.semconv-stability.preview")
    val preview = test(source.name + "PreviewSemconvWithPeerServiceMapping")
    assertThat(preview.systemProperties)
      .containsEntry(mappingKey, mapping)
      .containsEntry("otel.semconv-stability.preview", "service.peer")
      .containsEntry("metadataConfig", "otel.semconv-stability.preview=service.peer,$mappingKey=$mapping")
    for (name in listOf(source.name, source.name + "WithPeerServiceMapping", preview.name)) {
      val execution = test(name)
      assertBinding(execution, source)
      assertThat(execution.systemProperties).containsEntry("testLatestDeps", true)
      assertThat(execution.allJvmArgs).containsOnlyOnce("-Djava.net.preferIPv4Stack=true")
      assertThat(execution.filter.includePatterns).containsExactly("*ClientTest")
      assertThat(execution.excludes).containsExactly("**/server/**")
    }
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
      .contains(source.name, source.name + "WithPeerServiceMapping", preview.name)
    assertThat(project.tasks.names).doesNotContain("testWithPeerServiceMapping")
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

  @JunitTest
  fun `group configuration is isolated and follows suite configuration before execution configuration`() {
    suite("test").executions {
      group {
        register("Connection") {
          systemProperty("order", "${systemProperties["order"]},execution")
          systemProperty("setting", "execution")
        }
        register("OtherConnection") {}
        configureEach {
          systemProperty("order", "${systemProperties["order"]},group")
          systemProperty("setting", "group")
          filter.includeTestsMatching("*ConnectionTest")
        }
      }
      register("Sibling") {}
      group {
        configureEach {
          systemProperty("order", "${systemProperties["order"]},other-group")
          filter.includeTestsMatching("*OtherTest")
        }
        register("OtherGroup") {}
      }
      configureEach {
        systemProperty("order", "suite")
        systemProperty("setting", "suite")
      }
    }

    for (name in listOf("test", "testSibling")) {
      assertThat(test(name).systemProperties)
        .containsEntry("order", "suite")
        .containsEntry("setting", "suite")
      assertThat(test(name).filter.includePatterns).isEmpty()
    }
    assertThat(test("testConnection").systemProperties)
      .containsEntry("order", "suite,group,execution")
      .containsEntry("setting", "execution")
    assertThat(test("testOtherConnection").systemProperties)
      .containsEntry("order", "suite,group")
      .containsEntry("setting", "group")
    for (name in listOf("testConnection", "testOtherConnection")) {
      assertThat(test(name).filter.includePatterns).containsExactly("*ConnectionTest")
    }
    assertThat(test("testOtherGroup").systemProperties)
      .containsEntry("order", "suite,other-group")
      .containsEntry("setting", "suite")
    assertThat(test("testOtherGroup").filter.includePatterns).containsExactly("*OtherTest")
  }

  @JunitTest
  fun `group registrations stay lazy and bind to their source suite and check without extra tasks`() {
    var configuredTasks = 0
    project.tasks.withType(Test::class.java).configureEach {
      configuredTasks++
    }
    val source = suites().register("clientTest", JvmTestSuite::class.java).get()
    val sources = project.extensions.getByType(SourceSetContainer::class.java).names.toSet()
    source.executions {
      group {}
      group {
        register("Connection") {}
        configureEach {
          systemProperty("group", true)
        }
      }
    }

    assertThat(configuredTasks).isZero()
    assertThat(project.tasks.withType(Test::class.java).names)
      .containsExactlyInAnyOrder("test", "clientTest", "clientTestConnection")
    val variant = test("clientTestConnection")
    assertThat(configuredTasks).isEqualTo(1)
    assertBinding(variant, source)
    assertThat(variant.systemProperties).containsEntry("group", true)
    assertThat(test("clientTest").systemProperties).doesNotContainKey("group")
    assertThat(project.extensions.getByType(SourceSetContainer::class.java).names).isEqualTo(sources)
    assertThat(project.tasks.getByName("check").taskDependencies.getDependencies(null).map { it.name })
      .contains("clientTest", "clientTestConnection")
  }

  @JunitTest
  fun `groups reject duplicate suffixes within and across scopes`() {
    for (scope in listOf("root-first", "root-last", "groups", "same-group")) {
      assertThatThrownBy {
        suites().register(scope, JvmTestSuite::class.java).get().executions {
          when (scope) {
            "root-first" -> {
              register("Variant") {}
              group { register("Variant") {} }
            }
            "root-last" -> {
              group { register("Variant") {} }
              register("Variant") {}
            }
            "groups" -> {
              group { register("Variant") {} }
              group { register("Variant") {} }
            }
            else -> group {
              register("Variant") {}
              register("Variant") {}
            }
          }
        }
      }.isInstanceOf(IllegalArgumentException::class.java)
        .hasMessage("Execution variant 'Variant' is already registered")
    }
    assertThatThrownBy {
      suite("test").executions {
        group { register("") {} }
      }
    }.isInstanceOf(IllegalArgumentException::class.java)
      .hasMessage("An execution variant needs a non-empty suffix")
  }

  @JunitTest
  fun `group telemetry composes independently with suite and execution settings`() {
    suite("test").executions {
      group {
        register("Legacy") {}
        register("Preview") {
          telemetryConfig("otel.shared", "execution")
          telemetryConfig("otel.preview", "true")
        }
        configureEach {
          telemetryConfig("otel.shared", "group")
          telemetryConfig("otel.connection", "true")
        }
      }
      group {
        configureEach { telemetryConfig("otel.other", "true") }
        register("Other") {}
      }
      register("Sibling") {}
      configureEach { telemetryConfig("otel.shared", "suite") }
    }

    assertThat(test("testPreview").systemProperties)
      .containsEntry("otel.shared", "execution")
      .containsEntry("otel.connection", "true")
      .containsEntry("otel.preview", "true")
      .containsEntry("metadataConfig", "otel.shared=execution,otel.connection=true,otel.preview=true")
      .doesNotContainKey("otel.other")
    assertThat(test("testLegacy").systemProperties)
      .containsEntry("otel.shared", "group")
      .containsEntry("metadataConfig", "otel.shared=group,otel.connection=true")
      .doesNotContainKeys("otel.preview", "otel.other")
    assertThat(test("testOther").systemProperties)
      .containsEntry("metadataConfig", "otel.shared=suite,otel.other=true")
      .doesNotContainKeys("otel.connection", "otel.preview")
    for (name in listOf("test", "testSibling")) {
      assertThat(test(name).systemProperties)
        .containsEntry("metadataConfig", "otel.shared=suite")
        .doesNotContainKeys("otel.connection", "otel.preview", "otel.other")
    }
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
