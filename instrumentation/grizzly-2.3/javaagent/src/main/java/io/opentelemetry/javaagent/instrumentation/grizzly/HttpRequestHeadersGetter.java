/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Iterator;
import org.glassfish.grizzly.http.HttpRequestPacket;

enum HttpRequestHeadersGetter implements ExtendedTextMapGetter<HttpRequestPacket> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpRequestPacket request) {
    return request.getHeaders().names();
  }

  @Override
  public String get(HttpRequestPacket request, String key) {
    return request.getHeader(key);
  }

  @Override
  public Iterator<String> getAll(HttpRequestPacket request, String key) {
    return request.getHeaders().values(key).iterator();
  }
}
