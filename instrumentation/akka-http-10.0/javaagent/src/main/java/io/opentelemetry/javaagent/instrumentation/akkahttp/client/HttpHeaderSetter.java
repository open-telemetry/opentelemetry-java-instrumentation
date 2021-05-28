/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import akka.http.javadsl.model.headers.RawHeader;
import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.context.propagation.TextMapSetter;

public class HttpHeaderSetter implements TextMapSetter<AkkaHttpHeaders> {

  public static final HttpHeaderSetter SETTER = new HttpHeaderSetter();

  @Override
  public void set(AkkaHttpHeaders carrier, String key, String value) {
    HttpRequest request = carrier.getRequest();
    if (request != null) {
      // It looks like this cast is only needed in Java, Scala would have figured it out
      carrier.setRequest(
          (HttpRequest) request.removeHeader(key).addHeader(RawHeader.create(key, value)));
    }
  }
}
