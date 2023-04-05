/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter

internal object KtorHttpClientAttributesGetter : HttpClientAttributesGetter<HttpRequestData, HttpResponse> {

  override fun getUrl(request: HttpRequestData) =
    request.url.toString()

  override fun getFlavor(request: HttpRequestData, response: HttpResponse?) =
    null

  override fun getMethod(request: HttpRequestData) =
    request.method.value

  override fun getRequestHeader(request: HttpRequestData, name: String) =
    request.headers.getAll(name).orEmpty()

  override fun getStatusCode(request: HttpRequestData, response: HttpResponse, error: Throwable?) =
    response.status.value

  override fun getResponseHeader(request: HttpRequestData, response: HttpResponse, name: String) =
    response.headers.getAll(name).orEmpty()
}
