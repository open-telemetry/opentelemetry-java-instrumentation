/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import io.opentelemetry.javaagent.instrumentation.vertx.client.AbstractVertxHttpAttributesGetter;
import io.vertx.core.http.HttpClientRequest;

final class Vertx4HttpAttributesGetter extends AbstractVertxHttpAttributesGetter {

  @Override
  public String getUrl(HttpClientRequest request) {
    String uri = request.getURI();
    if (!isAbsolute(uri)) {
      uri = request.absoluteURI();
    }
    return uri;
  }

  private static boolean isAbsolute(String uri) {
    return uri.startsWith("http://") || uri.startsWith("https://");
  }

  @Override
  public String getMethod(HttpClientRequest request) {
    return request.getMethod().name();
  }
}
