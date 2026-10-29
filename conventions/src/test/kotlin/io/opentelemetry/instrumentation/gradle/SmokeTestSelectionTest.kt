/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test as JunitTest

class SmokeTestSelectionTest {
  private val project = ProjectBuilder.builder().build().also {
    it.pluginManager.apply("java-library")
  }

  @JunitTest
  fun `representative task has identical cache inputs on PR and main`() {
    for (suite in appServerSuites + listOf(null)) {
      val main = task(suite, "representative")
      val pr = task(suite, "representative", reduced = true)

      assertThat(selectionInputs(pr)).isEqualTo(selectionInputs(main))
      assertThat(pr.inputs.files.files).isEqualTo(main.inputs.files.files)
      assertThat(main.systemProperties).containsEntry("appServerTestGroup", "representative")
      assertThat(selectionInputs(task(suite, "remaining"))).isNotEqualTo(selectionInputs(main))
    }
  }

  @JunitTest
  fun `default and explicit full suites retain full selection`() {
    val full = task(null)
    assertThat(full.includes).isEmpty()
    assertThat(full.excludes).isEmpty()
    assertThat(full.systemProperties).doesNotContainKey("appServerTestGroup")
    for (suite in appServerSuites) {
      val selected = task(suite)
      assertThat(selected.includes).containsExactly("**/${suite.replaceFirstChar { it.uppercase() }}*.*")
      assertThat(selected.excludes).isEmpty()
      assertThat(selected.systemProperties).doesNotContainKey("appServerTestGroup")
      assertThat(task(suite, reduced = true).systemProperties)
        .containsEntry("appServerTestGroup", "representative")
    }
    assertThat(task(null, "remaining").includes).hasSize(appServerSuites.size)
  }

  @JunitTest
  fun `non app server recipes do not consume app server selection or OpenJ9 policy`() {
    for (suite in listOf("other", "spring-aot", "none")) {
      val full = task(suite)
      val reduced = task(suite, reduced = true, skipOpenJ9 = true)
      assertThat(selectionInputs(reduced)).isEqualTo(selectionInputs(full))
      assertThat(reduced.systemProperties).doesNotContainKey("appServerTestGroup")
      assertThat(task(suite, "representative").excludes).contains("**/*")
      assertThat(task(suite, "remaining").excludes).contains("**/*")
    }
    assertThat(task("none").excludes).containsExactly("**/*")
    assertThat(task("spring-aot").includes).containsExactly("**/SpringBootAotSmokeTest.*")
    assertThat(task("other").excludes).hasSize(appServerSuites.size + 1)
  }

  @JunitTest
  fun `explicit OpenJ9 filtering applies equally to both partitions`() {
    for (group in listOf(null, "representative", "remaining")) {
      assertThat(task("tomee", group, skipOpenJ9 = true).excludes)
        .containsExactly("**/*Openj9*.*")
      assertThat(task("tomee", group).excludes).isEmpty()
    }
  }

  @JunitTest
  fun `platform cache input follows Windows container selection and Linux override`() {
    assertThat(smokeTestUsesWindowsContainers("Windows Server 2025", "")).isTrue()
    assertThat(smokeTestUsesWindowsContainers("WINDOWS", "0")).isTrue()
    assertThat(smokeTestUsesWindowsContainers("Windows Server 2025", "1")).isFalse()
    assertThat(smokeTestUsesWindowsContainers("Linux", "")).isFalse()
    assertThat(smokeTestUsesWindowsContainers("Linux", "1")).isFalse()
    assertThat(task("tomee").inputs.properties.getValue("useWindowsContainers"))
      .isEqualTo(smokeTestUsesWindowsContainers(System.getProperty("os.name"), System.getenv("USE_LINUX_CONTAINERS") ?: ""))
  }

  @JunitTest
  fun `unknown suites fail rather than running everything`() {
    assertThatThrownBy { task("unknown") }
      .isInstanceOf(GradleException::class.java)
      .hasMessage("Unknown smoke test suite: unknown")
  }

  private fun task(
    suite: String?,
    group: String? = null,
    reduced: Boolean = false,
    skipOpenJ9: Boolean = false,
  ): Test = project.tasks.register("selection${project.tasks.size}", Test::class.java).get().also {
    it.useJUnitPlatform()
    it.testClassesDirs = project.files("classes")
    it.classpath = project.files("runtime")
    it.selectSmokeTests(suite, group, reduced, skipOpenJ9)
  }

  private fun selectionInputs(task: Test) = mapOf(
    "systemProperties" to task.inputs.properties.getValue("systemProperties"),
    "useWindowsContainers" to task.inputs.properties.getValue("useWindowsContainers"),
    "includes" to task.includes,
    "excludes" to task.excludes,
    "includePatterns" to task.filter.includePatterns,
    "excludePatterns" to task.filter.excludePatterns,
  )

  companion object {
    private val appServerSuites = listOf("payara", "jetty", "liberty", "tomcat", "tomee", "websphere", "wildfly")
  }
}
