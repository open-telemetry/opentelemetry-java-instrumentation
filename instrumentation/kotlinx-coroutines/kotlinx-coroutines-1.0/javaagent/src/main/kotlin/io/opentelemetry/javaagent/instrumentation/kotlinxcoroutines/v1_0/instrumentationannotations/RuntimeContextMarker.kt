/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

// Only the runtime applies thread context elements, so while this one is applied the runtime is
// running a coroutine with the callee's context. A callback that makes the span context current
// itself does not apply it.
class RuntimeContextMarker : ThreadContextElement<RuntimeContextMarker?> {
  companion object Key : CoroutineContext.Key<RuntimeContextMarker> {
    private val applied = ThreadLocal<RuntimeContextMarker?>()
  }

  override val key: CoroutineContext.Key<RuntimeContextMarker>
    get() = Key

  fun isApplied(): Boolean = applied.get() === this

  override fun updateThreadContext(context: CoroutineContext): RuntimeContextMarker? {
    val previous = applied.get()
    applied.set(this)
    return previous
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: RuntimeContextMarker?) {
    applied.set(oldState)
  }
}
