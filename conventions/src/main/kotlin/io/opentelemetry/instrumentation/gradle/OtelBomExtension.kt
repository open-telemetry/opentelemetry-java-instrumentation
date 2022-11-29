/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.util.function.Predicate

abstract class OtelBomExtension {
  abstract val projectFilter: Property<Predicate<Project>>
}
