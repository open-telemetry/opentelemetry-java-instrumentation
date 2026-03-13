/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static java.util.Collections.emptyIterator;
import static java.util.stream.Collectors.toList;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

final class AkkaHttpServerHeaders implements TextMapGetter<HttpRequest> {

  @Override
  public Iterable<String> keys(HttpRequest httpRequest) {
    return StreamSupport.stream(httpRequest.getHeaders().spliterator(), false)
        .map(HttpHeader::lowercaseName)
        .collect(toList());
  }

  @Override
  public String get(@Nullable HttpRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }
    Optional<HttpHeader> header = carrier.getHeader(key);
    return header.map(HttpHeader::value).orElse(null);
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpRequest carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }
    String headerName = key.toLowerCase(Locale.ROOT);
    List<String> result = new ArrayList<>();
    for (HttpHeader header : carrier.getHeaders()) {
      if (header.is(headerName)) {
        result.add(header.value());
      }
    }
    return result.iterator();
  }
}
