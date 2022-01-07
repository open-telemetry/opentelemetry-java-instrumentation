/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

enum AkkaHttpServerHeaders implements TextMapGetter<HttpRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpRequest httpRequest) {
    return StreamSupport.stream(httpRequest.getHeaders().spliterator(), false)
        .map(HttpHeader::lowercaseName)
        .collect(Collectors.toList());
  }

  @Override
  public String get(HttpRequest carrier, String key) {
    Optional<HttpHeader> header = carrier.getHeader(key);
    return header.map(HttpHeader::value).orElse(null);
  }
}
