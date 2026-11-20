/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

// Not a companion object: its synthetic constructor references DefaultConstructorMarker, which is
// not public in older kotlin-stdlib versions.
private object RuntimeContextMarkerKey : CoroutineContext.Key<RuntimeContextMarker> {
  val applied = ThreadLocal<RuntimeContextMarker?>()
}

// Only the runtime applies thread context elements, so while this one is applied the runtime is
// running a coroutine with the callee's context. A callback that makes the span context current
// itself does not apply it.
class RuntimeContextMarker : ThreadContextElement<RuntimeContextMarker?> {
  override val key: CoroutineContext.Key<RuntimeContextMarker>
    get() = RuntimeContextMarkerKey

  fun isApplied(): Boolean = RuntimeContextMarkerKey.applied.get() === this

  override fun updateThreadContext(context: CoroutineContext): RuntimeContextMarker? {
    val previous = RuntimeContextMarkerKey.applied.get()
    RuntimeContextMarkerKey.applied.set(this)
    return previous
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: RuntimeContextMarker?) {
    RuntimeContextMarkerKey.applied.set(oldState)
  }
}
