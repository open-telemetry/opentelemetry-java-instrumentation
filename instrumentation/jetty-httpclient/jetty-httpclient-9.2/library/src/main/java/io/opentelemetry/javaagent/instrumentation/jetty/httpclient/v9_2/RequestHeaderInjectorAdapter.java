/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestHeaderInjectorAdapter implements TextMapSetter<Request> {

  public static final RequestHeaderInjectorAdapter SETTER = new RequestHeaderInjectorAdapter();

  private static final Logger LOG = LoggerFactory.getLogger(RequestHeaderInjectorAdapter.class);

  @Override
  public void set(@Nullable Request request, String key, String value) {
    if (request != null) {
      // dedupe header fields here
      HttpField removed = request.getHeaders().remove(key);
      if (removed != null) {
        LOG.warn("Attempt to add a dupe header field {}, {}", key, removed.getValue());
      }
      request.header(key, value);
    }
  }
}
