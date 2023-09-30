/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.client;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.pekko.http.javadsl.model.headers.RawHeader;
import org.apache.pekko.http.scaladsl.model.HttpRequest;

public class HttpHeaderSetter implements TextMapSetter<HttpHeaderSetter.PekkoHttpHeaders> {

  private final ContextPropagators contextPropagators;

  public HttpHeaderSetter(ContextPropagators contextPropagators) {
    this.contextPropagators = contextPropagators;
  }

  @Override
  public void set(PekkoHttpHeaders carrier, String key, String value) {
    HttpRequest request = carrier.getRequest();
    if (request != null) {
      // It looks like this cast is only needed in Java, Scala would have figured it out
      carrier.setRequest(
          (HttpRequest) request.removeHeader(key).addHeader(RawHeader.create(key, value)));
    }
  }

  public HttpRequest inject(HttpRequest original) {
    PekkoHttpHeaders carrier = new PekkoHttpHeaders(original);
    contextPropagators.getTextMapPropagator().inject(Context.current(), carrier, this);
    return carrier.getRequest();
  }

  static class PekkoHttpHeaders {
    private HttpRequest request;

    public PekkoHttpHeaders(HttpRequest request) {
      this.request = request;
    }

    public HttpRequest getRequest() {
      return request;
    }

    public void setRequest(HttpRequest request) {
      this.request = request;
    }
  }
}
