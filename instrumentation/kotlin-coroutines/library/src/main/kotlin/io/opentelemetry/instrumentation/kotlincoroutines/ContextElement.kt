/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kotlincoroutines

import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

internal class ContextElement(val otelContext: Context) : ThreadContextElement<Scope> {

  companion object Key : CoroutineContext.Key<ContextElement>

  override val key: CoroutineContext.Key<ContextElement> get() = Key

  override fun updateThreadContext(context: CoroutineContext): Scope {
    return otelContext.makeCurrent()
  }

  override fun restoreThreadContext(context: CoroutineContext, scope: Scope) {
    scope.close()
  }
}
