/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test

fun Test.selectSmokeTests(
  suite: String?,
  appServerGroup: String? = null,
  reducedAppServerTests: Boolean = false,
  skipOpenJ9: Boolean = false,
) {
  // Match TestContainerManager.useWindowsContainers(), including its Linux-container override.
  inputs.property(
    "useWindowsContainers",
    project.providers.systemProperty("os.name")
      .zip(project.providers.environmentVariable("USE_LINUX_CONTAINERS").orElse("")) { os, linux ->
        smokeTestUsesWindowsContainers(os, linux)
      },
  )
  val appServerSuites = mapOf(
    "payara" to "**/Payara*.*",
    "jetty" to "**/Jetty*.*",
    "liberty" to "**/Liberty*.*",
    "tomcat" to "**/Tomcat*.*",
    "tomee" to "**/Tomee*.*",
    "websphere" to "**/Websphere*.*",
    "wildfly" to "**/Wildfly*.*",
  )
  val suites = appServerSuites + ("spring-aot" to "**/SpringBootAotSmokeTest.*")
  when {
    suite == null -> {
      if (appServerGroup != null) {
        include(appServerSuites.values)
      }
    }
    suite in suites -> include(suites.getValue(suite))
    suite == "other" -> exclude(suites.values)
    suite == "none" -> exclude("**/*")
    else -> throw GradleException("Unknown smoke test suite: $suite")
  }

  val includesAppServers = suite == null || suite in appServerSuites
  if (appServerGroup != null && !includesAppServers) {
    // An empty partition must not turn into an unfiltered Test task.
    exclude("**/*")
  }
  if (includesAppServers) {
    if (appServerGroup != null) {
      systemProperty("appServerTestGroup", appServerGroup)
    } else if (reducedAppServerTests) {
      systemProperty("appServerTestGroup", "representative")
    }
    if (skipOpenJ9) {
      exclude("**/*Openj9*.*")
    }
  }
}

internal fun smokeTestUsesWindowsContainers(os: String, linuxContainers: String): Boolean =
  linuxContainers != "1" && os.lowercase().contains("windows")
