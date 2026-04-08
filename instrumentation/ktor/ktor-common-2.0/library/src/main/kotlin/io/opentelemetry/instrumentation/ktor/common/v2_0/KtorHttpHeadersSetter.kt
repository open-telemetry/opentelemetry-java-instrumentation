/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0

import io.ktor.client.request.*
import io.opentelemetry.context.propagation.TextMapSetter

internal object KtorHttpHeadersSetter : TextMapSetter<HttpRequestBuilder> {

  override fun set(carrier: HttpRequestBuilder?, key: String, value: String) {
    carrier?.headers?.set(key, value)
  }
}
