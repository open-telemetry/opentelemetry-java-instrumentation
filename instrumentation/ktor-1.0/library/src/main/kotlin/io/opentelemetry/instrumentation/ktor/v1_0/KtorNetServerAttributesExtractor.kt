/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.features.*
import io.ktor.request.*
import io.ktor.response.*
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

internal class KtorNetServerAttributesExtractor : NetServerAttributesExtractor<ApplicationRequest, ApplicationResponse>() {
  override fun transport(request: ApplicationRequest): String {
    return SemanticAttributes.NetTransportValues.IP_TCP
  }

  override fun peerName(request: ApplicationRequest): String {
    return request.origin.host
  }

  override fun peerPort(request: ApplicationRequest): Int {
    return request.origin.port
  }

  override fun peerIp(request: ApplicationRequest): String {
    return request.origin.remoteHost
  }
}
