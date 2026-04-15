/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter
import io.opentelemetry.instrumentation.ktor.common.v2_0.internal.isIpAddress
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal object KtorHttpServerAttributesGetter : HttpServerAttributesGetter<ApplicationRequest, ApplicationResponse> {

  private val getRemoteAddressMethodHandle: MethodHandle? = getRemoteAddressMethodHandle()

  private fun getRemoteAddressMethodHandle(): MethodHandle? = try {
    MethodHandles.lookup().findVirtual(RequestConnectionPoint::class.java, "getRemoteAddress", MethodType.methodType(String::class.java))
  } catch (_: Exception) {
    null
  }

  override fun getHttpRequestMethod(request: ApplicationRequest): String = request.httpMethod.value

  override fun getHttpRequestHeader(request: ApplicationRequest, name: String): List<String> = request.headers.getAll(name) ?: emptyList()

  override fun getHttpResponseStatusCode(request: ApplicationRequest, response: ApplicationResponse, error: Throwable?): Int? = response.status()?.value

  override fun getHttpResponseHeader(request: ApplicationRequest, response: ApplicationResponse, name: String): List<String> = response.headers.allValues().getAll(name) ?: emptyList()

  override fun getUrlScheme(request: ApplicationRequest): String = request.origin.scheme

  override fun getUrlPath(request: ApplicationRequest): String = request.path()

  override fun getUrlQuery(request: ApplicationRequest): String = request.queryString()

  override fun getNetworkProtocolName(request: ApplicationRequest, response: ApplicationResponse?): String? = if (request.httpVersion.startsWith("HTTP/")) "http" else null

  override fun getNetworkProtocolVersion(request: ApplicationRequest, response: ApplicationResponse?): String? = if (request.httpVersion.startsWith("HTTP/")) request.httpVersion.substring("HTTP/".length) else null

  override fun getNetworkPeerAddress(request: ApplicationRequest, response: ApplicationResponse?): String? {
    if (getRemoteAddressMethodHandle == null) {
      return null
    }

    val remote = try {
      getRemoteAddressMethodHandle.invoke(request.local) as String
    } catch (_: Throwable) {
      "unknown"
    }
    if ("unknown" != remote && isIpAddress(remote)) {
      return remote
    }
    return null
  }
}
