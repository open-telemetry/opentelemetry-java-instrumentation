/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.base.TestingExtension
import org.gradle.testing.base.TestSuite

class TestSuiteExecutionsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("jvm-test-suite")
    project.extensions.getByType(TestingExtension::class.java).suites
      .withType(JvmTestSuite::class.java).configureEach {
        (this as ExtensionAware).extensions.add("executions", TestSuiteExecutions(project, this))
      }
  }
}

fun JvmTestSuite.executions(action: Action<TestSuiteExecutions>) {
  (this as ExtensionAware).extensions.getByType(TestSuiteExecutions::class.java).configure(action)
}

fun NamedDomainObjectCollection<TestSuite>.executions(
  vararg suiteNames: String,
  action: Action<TestSuiteExecutions>,
) {
  if (suiteNames.isEmpty()) {
    withType(JvmTestSuite::class.java).configureEach { executions(action) }
  } else {
    suiteNames.forEach { suiteName ->
      named(suiteName).configure {
        require(this is JvmTestSuite) { "Test suite '$suiteName' is not a JVM test suite" }
        executions(action)
      }
    }
  }
}

/** Configuration of a suite's existing default execution and explicit variants. */
class TestSuiteExecutions internal constructor(
  private val project: Project,
  private val suite: JvmTestSuite,
) {
  private val common = mutableListOf<Action<Test>>()
  private val defaultOnly = mutableListOf<Action<Test>>()
  private val variants = linkedMapOf<String, Action<Test>>()
  private var configured = false

  fun configureEach(action: Action<Test>) {
    common.add(action)
  }

  /** Configure the suite's default execution after common configuration. */
  fun configureDefault(action: Action<Test>) {
    defaultOnly.add(action)
  }

  /** The suffix is appended to the suite name, e.g. test + Experimental. */
  fun register(suffix: String, action: Action<Test>) {
    require(suffix.isNotBlank()) { "An execution variant needs a non-empty suffix" }
    require(!variants.containsKey(suffix)) { "Execution variant '$suffix' is already registered" }
    variants[suffix] = action
  }

  internal fun configure(action: Action<TestSuiteExecutions>) {
    check(!configured) { "Declare one executions block per suite" }
    configured = true
    action.execute(this)

    // Collect the whole block first so common configuration always precedes variant configuration.
    suite.targets.configureEach {
      testTask.configure {
        common.forEach { it.execute(this) }
        defaultOnly.forEach { it.execute(this) }
      }
      project.tasks.named("check").configure {
        dependsOn(testTask)
      }
    }
    variants.forEach { (suffix, variant) ->
      val task = project.tasks.register(suite.name + suffix, Test::class.java) {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath
        common.forEach { it.execute(this) }
        variant.execute(this)
      }
      project.tasks.named("check").configure {
        dependsOn(task)
      }
    }
  }
}

/** Apply a telemetry setting to the test JVM and to the collected metadata profile. */
fun Test.telemetryConfig(key: String, value: String) {
  val settings = extensions.findByType(TelemetrySettings::class.java)
    ?: TelemetrySettings().also { extensions.add("telemetrySettings", it) }
  settings.values[key] = value
  systemProperty(key, value)
  systemProperty("metadataConfig", settings.values.entries.joinToString(",") { "${it.key}=${it.value}" })
}

private class TelemetrySettings {
  val values = linkedMapOf<String, String>()
}
