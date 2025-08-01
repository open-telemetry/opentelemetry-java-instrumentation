/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.flow

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationCallback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion

fun <REQUEST, RESPONSE> onComplete(flow: Flow<*>, handler: AsyncOperationCallback<REQUEST, RESPONSE>, context: Context, request: REQUEST & Any): Flow<*> = flow.onCompletion { cause: Throwable? ->
  handler.onEnd(context, request, null, cause)
}
