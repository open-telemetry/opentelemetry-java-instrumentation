/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kotlincoroutines

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge
import kotlin.coroutines.CoroutineContext

/**
 * Returns a [CoroutineContext] which will make this [Context] current when resuming a coroutine
 * and restores the previous [Context] on suspension.
 */
fun Context.asContextElement(): CoroutineContext {
  return ContextElement(this)
}

/**
 * Returns a [CoroutineContext] which will make this [Span] current when resuming a coroutine
 * and restores the previous [Context] on suspension.
 */
fun Span.asContextElement(): CoroutineContext {
  return ContextElement(Java8BytecodeBridge.currentContext().with(this))
}
