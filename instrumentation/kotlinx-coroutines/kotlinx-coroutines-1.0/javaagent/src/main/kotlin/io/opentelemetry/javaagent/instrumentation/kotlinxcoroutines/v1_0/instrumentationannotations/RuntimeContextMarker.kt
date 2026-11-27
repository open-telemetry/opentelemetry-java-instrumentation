/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations

import io.opentelemetry.context.Scope
import kotlinx.coroutines.ThreadContextElement
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

// Not a companion object: its synthetic constructor references DefaultConstructorMarker, which is
// not public in older kotlin-stdlib versions.
private object RuntimeContextMarkerKey : CoroutineContext.Key<RuntimeContextMarker> {
  val applied = ThreadLocal<RuntimeContextMarkerApplication?>()
}

// The innermost application of a marker on this thread, if the runtime is running a coroutine with
// a callee's context on it.
fun currentRuntimeContextMarkerApplication(): RuntimeContextMarkerApplication? = RuntimeContextMarkerKey.applied.get()

// One application of a marker on a thread. Scopes deferred to it are closed when the runtime
// restores it, most recent first.
class RuntimeContextMarkerApplication internal constructor(private val previous: RuntimeContextMarkerApplication?) {
  private var deferred: ArrayDeque<Scope>? = null
  private var restored = false

  fun defer(scope: Scope) {
    val scopes = deferred ?: ArrayDeque<Scope>().also { deferred = it }
    scopes.push(scope)
  }

  // Returns whether the scope was still deferred, that is, not closed by the runtime yet.
  fun undefer(scope: Scope): Boolean = deferred?.remove(scope) == true

  internal fun restore() {
    // UndispatchedCoroutine can replay the restore of a snapshot taken without a dispatcher.
    if (restored) {
      return
    }
    restored = true
    deferred?.let { scopes ->
      while (!scopes.isEmpty()) {
        scopes.pop().close()
      }
    }
    RuntimeContextMarkerKey.applied.set(previous)
  }
}

// Added to the callee's context, so the runtime applies it whenever it runs a coroutine with that
// context, and, as the last element added, restores it just before the span's scope. A callback that
// makes the span context current itself does not apply it.
class RuntimeContextMarker : ThreadContextElement<RuntimeContextMarkerApplication> {
  override val key: CoroutineContext.Key<RuntimeContextMarker>
    get() = RuntimeContextMarkerKey

  override fun updateThreadContext(context: CoroutineContext): RuntimeContextMarkerApplication {
    val application = RuntimeContextMarkerApplication(RuntimeContextMarkerKey.applied.get())
    RuntimeContextMarkerKey.applied.set(application)
    return application
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: RuntimeContextMarkerApplication) {
    oldState.restore()
  }
}
