/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

class MetadataCollectionArgumentsProvider(
  collectMetadata: Boolean,
  taskPath: String,
  requestedTasks: List<String>
) : CommandLineArgumentProvider {
  @get:Input
  val collectMetadata: Boolean = collectMetadata && taskPath in requestedTasks

  override fun asArguments(): Iterable<String> =
    listOf("-DcollectMetadata=$collectMetadata")
}
