/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.glassfish.grizzly.http.HttpRequestPacket;

final class HttpRequestHeadersGetter implements TextMapGetter<HttpRequestPacket> {

  @Override
  public Iterable<String> keys(HttpRequestPacket request) {
    return request.getHeaders().names();
  }

  @Nullable
  @Override
  public String get(@Nullable HttpRequestPacket request, String key) {
    if (request == null) {
      return null;
    }
    return request.getHeader(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpRequestPacket request, String key) {
    if (request == null) {
      return emptyIterator();
    }
    return request.getHeaders().values(key).iterator();
  }
}
