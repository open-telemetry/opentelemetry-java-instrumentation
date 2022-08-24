/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0

import io.ktor.server.request.*
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter
import io.opentelemetry.instrumentation.ktor.isIpAddress
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

internal class KtorNetServerAttributesGetter : NetServerAttributesGetter<ApplicationRequest> {
  override fun transport(request: ApplicationRequest): String {
    return SemanticAttributes.NetTransportValues.IP_TCP
  }

  override fun sockPeerPort(request: ApplicationRequest): Int? {
    return null
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

  override fun sockFamily(request: ApplicationRequest): String? {
    return null
  }

  override fun sockHostAddr(request: ApplicationRequest): String? {
    return null
  }

  override fun sockHostName(request: ApplicationRequest): String? {
    return null
  }

  override fun sockHostPort(request: ApplicationRequest): Int? {
    return null
  }
}
