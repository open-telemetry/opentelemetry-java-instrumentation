/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v1_0

import io.ktor.features.*
import io.ktor.request.*
import io.ktor.response.*
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

internal enum class KtorHttpServerAttributesGetter :
  HttpServerAttributesGetter<ApplicationRequest, ApplicationResponse> {
  INSTANCE, ;

  override fun getMethod(request: ApplicationRequest): String {
    return request.httpMethod.value
  }

  override fun getRequestHeader(request: ApplicationRequest, name: String): List<String> {
    return request.headers.getAll(name) ?: emptyList()
  }

  override fun getStatusCode(request: ApplicationRequest, response: ApplicationResponse, error: Throwable?): Int? {
    return response.status()?.value
  }

  override fun getResponseHeader(request: ApplicationRequest, response: ApplicationResponse, name: String): List<String> {
    return response.headers.allValues().getAll(name) ?: emptyList()
  }

  override fun getFlavor(request: ApplicationRequest): String? {
    return when (request.httpVersion) {
      "HTTP/1.1" -> SemanticAttributes.HttpFlavorValues.HTTP_1_1
      "HTTP/2.0" -> SemanticAttributes.HttpFlavorValues.HTTP_2_0
      else -> null
    }
  }

  override fun getTarget(request: ApplicationRequest): String {
    return request.uri
  }

  override fun getRoute(request: ApplicationRequest): String? {
    return null
  }

  override fun getScheme(request: ApplicationRequest): String {
    return request.origin.scheme
  }
}
