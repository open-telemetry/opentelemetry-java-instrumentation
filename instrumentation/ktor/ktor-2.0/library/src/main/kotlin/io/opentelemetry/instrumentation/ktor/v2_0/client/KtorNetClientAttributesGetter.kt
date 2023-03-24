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

  override fun getProtocolName(request: HttpRequestData?, response: HttpResponse?): String? =
    response?.version?.name

  override fun getProtocolVersion(request: HttpRequestData?, response: HttpResponse?): String? {
    val version = response?.version ?: return null
    return "${version.major}.${version.minor}"
  }

  override fun getPeerName(request: HttpRequestData) = request.url.host

  override fun getPeerPort(request: HttpRequestData) = request.url.port
}
