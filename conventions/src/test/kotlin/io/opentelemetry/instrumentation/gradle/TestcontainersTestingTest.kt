/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.base.TestingExtension
import org.junit.jupiter.api.Test as JunitTest

class TestcontainersTestingTest {
  @JunitTest
  fun `projects without opt in do not register or use the service`() {
    val project = project()

    assertThat(project.gradle.sharedServices.registrations.names).doesNotContain("testcontainersBuildService")
    assertThat(test(project, "test").requiredServices.elements).isEmpty()
  }

  @JunitTest
  fun `opt in only configures scheduling and can precede the java plugin`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("otel.testcontainers-testing")

    assertThat(project.plugins.hasPlugin("java")).isFalse()
    assertThat(project.configurations).isEmpty()
    assertThat(project.tasks.withType(Test::class.java)).isEmpty()

    project.pluginManager.apply("java-library")

    val service = project.gradle.sharedServices.registrations.getByName("testcontainersBuildService").service
    assertThat(test(project, "test").requiredServices.elements).containsExactly(service)
  }

  @JunitTest
  fun `opted in projects share one build wide two task limiter`() {
    val root = project()
    val first = project("first", root)
    val second = project("second", root)
    first.pluginManager.apply("otel.testcontainers-testing")
    second.pluginManager.apply("otel.testcontainers-testing")

    val registration = root.gradle.sharedServices.registrations.getByName("testcontainersBuildService")
    assertThat(root.gradle.sharedServices.registrations.names).containsExactly("testcontainersBuildService")
    assertThat(registration.maxParallelUsages.get()).isEqualTo(2)
    assertThat(test(first, "test").requiredServices.elements).containsExactly(registration.service)
    assertThat(test(second, "test").requiredServices.elements).containsExactly(registration.service)
    assertThat(test(root, "test").requiredServices.elements).isEmpty()
  }

  @JunitTest
  fun `wires existing and future suites variants and independent test tasks lazily`() {
    val project = project()
    project.pluginManager.apply(TestSuiteExecutionsPlugin::class.java)
    val suites = project.extensions.getByType(TestingExtension::class.java).suites
    val default = suites.getByName("test") as JvmTestSuite
    default.executions {
      register("Experimental") {}
    }
    test(project, "test")
    project.tasks.register("independentTest", Test::class.java)
    var configuredTasks = 0
    project.tasks.withType(Test::class.java).configureEach {
      configuredTasks++
    }

    project.pluginManager.apply("otel.testcontainers-testing")

    suites.register("unitTests", JvmTestSuite::class.java)
    suites.register("versionTest", JvmTestSuite::class.java).get().executions {
      register("Experimental") {}
    }
    project.tasks.register("laterTest", Test::class.java)
    assertThat(configuredTasks).isEqualTo(1)

    val service = project.gradle.sharedServices.registrations.getByName("testcontainersBuildService").service
    val tasks = project.tasks.withType(Test::class.java).toList()
    assertThat(tasks.map { it.name }).containsExactlyInAnyOrder(
      "test", "testExperimental", "unitTests", "versionTest", "versionTestExperimental",
      "independentTest", "laterTest"
    )
    tasks.forEach {
      assertThat(it.requiredServices.elements).containsExactly(service)
    }
  }

  private fun project(name: String = "root", parent: Project? = null): Project =
    ProjectBuilder.builder().withName(name).withParent(parent).build().also {
      it.pluginManager.apply("java-library")
    }

  private fun test(project: Project, name: String) = project.tasks.getByName(name) as Test
}
