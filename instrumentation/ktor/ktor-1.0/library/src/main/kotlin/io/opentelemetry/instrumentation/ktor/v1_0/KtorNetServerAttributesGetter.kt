/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.request.*
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter
import io.opentelemetry.instrumentation.ktor.isIpAddress
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

internal class KtorNetServerAttributesGetter : NetServerAttributesGetter<ApplicationRequest> {
  override fun transport(request: ApplicationRequest): String {
    return SemanticAttributes.NetTransportValues.IP_TCP
  }

  override fun sockPeerAddr(request: ApplicationRequest): String? {
    val remote = request.local.remoteHost
    if ("unknown" != remote && isIpAddress(remote)) {
      return remote
    }
    return null
  }

  override fun hostName(request: ApplicationRequest): String {
    return request.local.host
  }

  override fun hostPort(request: ApplicationRequest): Int {
    return request.local.port
  }
}
