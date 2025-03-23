/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.common

import io.ktor.server.request.*
import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter
import java.util.Collections

internal object ApplicationRequestGetter : ExtendedTextMapGetter<ApplicationRequest> {
  override fun keys(carrier: ApplicationRequest): Iterable<String> = carrier.headers.names()

  override fun get(carrier: ApplicationRequest?, name: String): String? = carrier?.headers?.get(name)

  override fun getAll(carrier: ApplicationRequest?, name: String): Iterator<String> = carrier?.headers?.getAll(name)?.iterator() ?: Collections.emptyIterator()
}
