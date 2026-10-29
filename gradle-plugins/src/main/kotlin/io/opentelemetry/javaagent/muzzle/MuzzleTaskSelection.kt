/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle

import org.gradle.api.Project

internal object MuzzleTaskSelection {
  fun isRequested(project: Project): Boolean {
    val startParameter = project.gradle.startParameter
    val projects = project.rootProject.allprojects
    val invocationDirectory = (startParameter.projectDir ?: startParameter.currentDir).toPath()
    val invocationProject = projects
      .filter { invocationDirectory.startsWith(it.projectDir.toPath()) }
      .maxByOrNull { it.projectDir.toPath().nameCount }
      ?: project.rootProject
    val aggregate = project.extensions.extraProperties
      .takeIf { it.has("otel.muzzle.aggregateTaskPath") }
      ?.get("otel.muzzle.aggregateTaskPath") as String?

    return startParameter.taskNames.any { requested ->
      val taskName = requested.substringAfterLast(':')
      val scope = if (requested.startsWith(":")) {
        requested.substringBeforeLast(':').ifEmpty { ":" }
      } else {
        (invocationProject.path.trimEnd(':') + ":" + requested)
          .substringBeforeLast(':').ifEmpty { ":" }
      }
      val knownScope = projects.any { it.path == scope }
      fun inScope(path: String) = scope == ":" || path == scope || path.startsWith("$scope:")

      when {
        taskName.matches(Regex("muzzle\\d+")) -> {
          // Projects outside the aggregate (including external plugin users) have no membership.
          aggregate == null || !knownScope ||
            (taskName == aggregate.substringAfterLast(':') && inScope(aggregate.substringBeforeLast(':')))
        }
        taskName == "muzzle" || taskName.startsWith("muzzle-Assert") ->
          !knownScope || inScope(project.path)
        // Gradle also accepts abbreviated project and task selectors. Be conservative rather
        // than omit checks when the selector cannot be identified exactly during configuration.
        taskName.isNotEmpty() &&
          ("muzzle".startsWith(taskName.substringBefore('-'), ignoreCase = true) ||
            taskName.matches(Regex("[mM][a-z]*[A-Z0-9].*"))) ->
          !knownScope || projects.none { inScope(it.path) && taskName in it.tasks.names }
        else -> false
      }
    }
  }
}
