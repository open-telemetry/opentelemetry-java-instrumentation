/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

internal class KtorHttpServerAttributesExtractor(capturedHttpHeaders: CapturedHttpHeaders) :
  HttpServerAttributesExtractor<ApplicationRequest, ApplicationResponse>(capturedHttpHeaders) {

  override fun method(request: ApplicationRequest): String {
    return request.httpMethod.value
  }

  override fun requestHeader(request: ApplicationRequest, name: String): List<String> {
    return request.headers.getAll(name) ?: emptyList()
  }

  override fun requestContentLength(request: ApplicationRequest, response: ApplicationResponse?): Long? {
    return null
  }

  override fun requestContentLengthUncompressed(request: ApplicationRequest, response: ApplicationResponse?): Long? {
    return null
  }

  override fun statusCode(request: ApplicationRequest, response: ApplicationResponse): Int? {
    return response.status()?.value
  }

  override fun responseContentLength(request: ApplicationRequest, response: ApplicationResponse): Long? {
    return null
  }

  override fun responseContentLengthUncompressed(request: ApplicationRequest, response: ApplicationResponse): Long? {
    return null
  }

  override fun responseHeader(request: ApplicationRequest, response: ApplicationResponse, name: String): List<String> {
    return response.headers.allValues().getAll(name) ?: emptyList()
  }

  override fun flavor(request: ApplicationRequest): String? {
    return when (request.httpVersion) {
      "HTTP/1.1" -> SemanticAttributes.HttpFlavorValues.HTTP_1_1
      "HTTP/2.0" -> SemanticAttributes.HttpFlavorValues.HTTP_2_0
      else -> null
    }
  }

  override fun target(request: ApplicationRequest): String {
    return request.uri
  }

  override fun route(request: ApplicationRequest): String? {
    return null
  }

  override fun scheme(request: ApplicationRequest): String {
    return request.origin.scheme
  }

  override fun serverName(request: ApplicationRequest, response: ApplicationResponse?): String? {
    return null
  }
}
