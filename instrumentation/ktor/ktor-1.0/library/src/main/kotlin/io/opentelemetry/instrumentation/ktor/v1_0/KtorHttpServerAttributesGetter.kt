/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.features.*
import io.ktor.request.*
import io.ktor.response.*
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter
import io.opentelemetry.instrumentation.ktor.isIpAddress

internal enum class KtorHttpServerAttributesGetter :
  HttpServerAttributesGetter<ApplicationRequest, ApplicationResponse> {
  INSTANCE, ;

  override fun getHttpRequestMethod(request: ApplicationRequest): String {
    return request.httpMethod.value
  }

  override fun getHttpRequestHeader(request: ApplicationRequest, name: String): List<String> {
    return request.headers.getAll(name) ?: emptyList()
  }

  override fun getHttpResponseStatusCode(request: ApplicationRequest, response: ApplicationResponse, error: Throwable?): Int? {
    return response.status()?.value
  }

  override fun getHttpResponseHeader(request: ApplicationRequest, response: ApplicationResponse, name: String): List<String> {
    return response.headers.allValues().getAll(name) ?: emptyList()
  }

  override fun getUrlScheme(request: ApplicationRequest): String {
    return request.origin.scheme
  }

  override fun getUrlPath(request: ApplicationRequest): String {
    return request.path()
  }

  override fun getUrlQuery(request: ApplicationRequest): String {
    return request.queryString()
  }

  override fun getNetworkProtocolName(request: ApplicationRequest, response: ApplicationResponse?): String? =
    if (request.httpVersion.startsWith("HTTP/")) "http" else null

  override fun getNetworkProtocolVersion(request: ApplicationRequest, response: ApplicationResponse?): String? =
    if (request.httpVersion.startsWith("HTTP/")) request.httpVersion.substring("HTTP/".length) else null

  override fun getNetworkPeerAddress(request: ApplicationRequest, response: ApplicationResponse?): String? {
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
