/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.delay

// top-level functions compile to static methods, which have no this in slot 0
@WithSpan
suspend fun topLevelAnnotated(
  @SpanAttribute intValue: Int,
  @SpanAttribute longValue: Long,
  @SpanAttribute("stringValue") s: String,
  unannotated: String
) {
  delay(10)
}
