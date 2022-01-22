/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.request.*
import io.ktor.response.*
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

internal class KtorNetServerAttributesGetter : NetServerAttributesGetter<ApplicationRequest> {
  override fun transport(request: ApplicationRequest): String {
    return SemanticAttributes.NetTransportValues.IP_TCP
  }

  override fun peerName(request: ApplicationRequest): String? {
    var remote = request.local.remoteHost
    if (remote != null && "unknown" != remote && !isIpAddress(remote)) {
      return remote
    }
    return null
  }

  override fun peerPort(request: ApplicationRequest): Int? {
    return null
  }

  override fun peerIp(request: ApplicationRequest): String? {
    var remote = request.local.remoteHost
    if (remote != null && "unknown" != remote && isIpAddress(remote)) {
      return remote
    }
    return null
  }
}
