/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpHeaderSetter implements TextMapSetter<Request> {

  private static final Logger LOG = LoggerFactory.getLogger(HttpHeaderSetter.class);

  @Override
  public void set(Request request, String key, String value) {
    if (request != null) {
      // dedupe header fields here
      HttpField removed = request.getHeaders().remove(key);
      if (removed != null) {
        LOG.debug("Attempt to add a dupe header field {}, {}", key, removed.getValue());
      }
      request.header(key, value);
    }
  }
}
