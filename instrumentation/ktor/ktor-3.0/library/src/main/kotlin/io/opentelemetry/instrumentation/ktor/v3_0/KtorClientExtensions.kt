/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0

import io.ktor.client.call.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.ktor.common.v2_0.internal.KtorClientTelemetryUtil

/**
 * Returns the OpenTelemetry [Context] carrying the client span created for this call, or `null` if
 * no span was created (for example if instrumentation is disabled, or the client span was
 * suppressed because a client span was already active).
 *
 * Intended for use inside the `HttpStatement.execute { }` block, where the client span is still
 * active. The client span ends when the call completes, after which mutations to it are silently
 * dropped.
 */
fun HttpClientCall.getOpenTelemetryContext(): Context? = KtorClientTelemetryUtil.getOpenTelemetryContext(this)

/**
 * Runs [block] with the client [Span] created for this call as the receiver. Does nothing if no
 * span was created (see [getOpenTelemetryContext]).
 *
 * Intended for use inside the `HttpStatement.execute { }` block, where the client span is still
 * active. The client span ends when the call completes, after which mutations to it are silently
 * dropped.
 */
inline fun HttpClientCall.withClientSpan(block: Span.() -> Unit) {
  val context = getOpenTelemetryContext() ?: return
  Span.fromContext(context).block()
}
