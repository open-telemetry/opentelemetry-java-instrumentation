/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;

import akka.http.javadsl.model.headers.RawHeader;
import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;

public class HttpHeaderSetter implements TextMapSetter<HttpHeaderSetter.AkkaHttpHeaders> {

  private final ContextPropagators contextPropagators;

  public HttpHeaderSetter(ContextPropagators contextPropagators) {
    this.contextPropagators = contextPropagators;
  }

  @Override
  public void set(AkkaHttpHeaders carrier, String key, String value) {
    HttpRequest request = carrier.getRequest();
    if (request != null) {
      // It looks like this cast is only needed in Java, Scala would have figured it out
      carrier.setRequest(
          (HttpRequest) request.removeHeader(key).addHeader(RawHeader.create(key, value)));
    }
  }

  public HttpRequest inject(HttpRequest original) {
    AkkaHttpHeaders carrier = new AkkaHttpHeaders(original);
    contextPropagators.getTextMapPropagator().inject(currentContext(), carrier, this);
    return carrier.getRequest();
  }

  static class AkkaHttpHeaders {
    private HttpRequest request;

    public AkkaHttpHeaders(HttpRequest request) {
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
