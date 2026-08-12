/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.provider.Property

abstract class InstrumentationVersionClassExtension {
  abstract val className: Property<String>
}
