/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;

class HttpHeaderSetter implements TextMapSetter<Request> {

  @Override
  public void set(@Nullable Request request, String key, String value) {
    if (request != null) {
      // dedupe header fields here with a put()
      request.getHeaders().put(key, value);
    }
  }
}
