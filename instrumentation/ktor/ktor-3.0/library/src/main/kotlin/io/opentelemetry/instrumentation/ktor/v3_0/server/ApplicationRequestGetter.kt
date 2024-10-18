/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v3_0.server

import io.ktor.server.request.*
import io.opentelemetry.context.propagation.TextMapGetter

internal object ApplicationRequestGetter : TextMapGetter<ApplicationRequest> {
  override fun keys(carrier: ApplicationRequest): Iterable<String> {
    return carrier.headers.names()
  }

  override fun get(carrier: ApplicationRequest?, name: String): String? {
    return carrier?.headers?.get(name)
  }
}
