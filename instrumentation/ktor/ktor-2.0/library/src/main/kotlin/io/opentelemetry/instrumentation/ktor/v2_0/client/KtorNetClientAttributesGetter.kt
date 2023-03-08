/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

internal object KtorNetClientAttributesGetter : NetClientAttributesGetter<HttpRequestData, HttpResponse> {

  override fun getTransport(request: HttpRequestData, response: HttpResponse?) = IP_TCP

  override fun getPeerName(request: HttpRequestData) = request.url.host

  override fun getPeerPort(request: HttpRequestData) = request.url.port
}
