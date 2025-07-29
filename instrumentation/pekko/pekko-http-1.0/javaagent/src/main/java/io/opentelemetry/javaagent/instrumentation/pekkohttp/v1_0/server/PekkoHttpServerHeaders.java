/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.scaladsl.model.HttpRequest;

enum PekkoHttpServerHeaders implements ExtendedTextMapGetter<HttpRequest> {
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

  @Override
  public Iterator<String> getAll(HttpRequest carrier, String key) {
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
