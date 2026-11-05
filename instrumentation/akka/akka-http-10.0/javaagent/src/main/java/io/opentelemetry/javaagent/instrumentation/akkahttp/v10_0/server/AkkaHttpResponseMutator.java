/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.RawHeader;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import java.util.ArrayList;
import java.util.List;

class AkkaHttpResponseMutator implements HttpServerResponseMutator<HttpResponse> {

  private final List<HttpHeader> headers = new ArrayList<>();

  @Override
  public void appendHeader(HttpResponse response, String name, String value) {
    headers.add(RawHeader.create(name, value));
  }

  static akka.http.scaladsl.model.HttpResponse customizeResponse(
      Context context, akka.http.scaladsl.model.HttpResponse response) {
    return (akka.http.scaladsl.model.HttpResponse)
        customizeResponse(context, (HttpResponse) response);
  }

  static HttpResponse customizeResponse(Context context, HttpResponse response) {
    // Akka responses are immutable, so capture added headers and build a new response.
    AkkaHttpResponseMutator responseMutator = new AkkaHttpResponseMutator();
    HttpServerResponseCustomizerHolder.getCustomizer()
        .customize(context, response, responseMutator);
    if (!responseMutator.headers.isEmpty()) {
      response = response.addHeaders(responseMutator.headers);
    }
    return response;
  }
}
