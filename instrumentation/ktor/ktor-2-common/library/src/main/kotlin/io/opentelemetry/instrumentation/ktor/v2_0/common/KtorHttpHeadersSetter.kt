/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.common

import io.ktor.client.request.*
import io.opentelemetry.context.propagation.TextMapSetter

internal object KtorHttpHeadersSetter : TextMapSetter<HttpRequestBuilder> {

  override fun set(carrier: HttpRequestBuilder?, key: String, value: String) {
    carrier?.headers?.set(key, value)
  }
}
