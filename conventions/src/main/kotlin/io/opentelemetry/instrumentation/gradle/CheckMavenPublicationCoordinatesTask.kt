/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class CheckMavenPublicationCoordinatesTask : DefaultTask() {
  @get:Input
  abstract val publicationCoordinates: ListProperty<String>

  @TaskAction
  fun checkPublicationCoordinates() {
    val duplicatePublications = publicationCoordinates.get()
      .groupBy { it.substringBefore('=') }
      .filterValues { it.size > 1 }

    if (duplicatePublications.isEmpty()) {
      return
    }

    val message = buildString {
      appendLine("Duplicate Maven publication coordinates:")
      duplicatePublications.toSortedMap().forEach { (coordinates, publications) ->
        appendLine("  $coordinates")
        publications.sorted().forEach { publication ->
          appendLine("    - ${publication.substringAfter('=')}")
        }
      }
    }
    throw GradleException(message)
  }
}
