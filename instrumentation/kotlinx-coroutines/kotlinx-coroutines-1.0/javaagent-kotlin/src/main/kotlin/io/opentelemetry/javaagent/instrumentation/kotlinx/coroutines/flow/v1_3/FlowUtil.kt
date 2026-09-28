/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinx.coroutines.flow.v1_3

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion

fun <REQUEST, RESPONSE, T> onComplete(
  flow: Flow<T>,
  instrumenter: Instrumenter<REQUEST, RESPONSE>,
  context: Context,
  request: REQUEST & Any,
): Flow<T> = flow.onCompletion { cause: Throwable? ->
  instrumenter.end(context, request, null, cause)
}
