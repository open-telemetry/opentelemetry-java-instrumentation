/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.request.*
import io.ktor.response.*
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter
import io.opentelemetry.instrumentation.ktor.isIpAddress

internal class KtorNetServerAttributesGetter : NetServerAttributesGetter<ApplicationRequest, ApplicationResponse> {

  override fun getNetworkProtocolName(request: ApplicationRequest, response: ApplicationResponse?): String? =
    if (request.httpVersion.startsWith("HTTP/")) "http" else null

  override fun getNetworkProtocolVersion(request: ApplicationRequest, response: ApplicationResponse?): String? =
    if (request.httpVersion.startsWith("HTTP/")) request.httpVersion.substring("HTTP/".length) else null

  override fun getClientSocketAddress(request: ApplicationRequest, response: ApplicationResponse?): String? {
    val remote = request.local.remoteHost
    if ("unknown" != remote && isIpAddress(remote)) {
      return remote
    }
    return null
  }

  override fun getServerAddress(request: ApplicationRequest): String {
    return request.local.host
  }

  override fun getServerPort(request: ApplicationRequest): Int {
    return request.local.port
  }
}
