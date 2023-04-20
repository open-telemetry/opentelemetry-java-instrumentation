/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

public enum UndertowHttpResponseMutator implements HttpServerResponseMutator<HttpServerExchange> {
  INSTANCE;

  UndertowHttpResponseMutator() {}

  @Override
  public void appendHeader(HttpServerExchange exchange, String name, String value) {
    exchange.getResponseHeaders().add(HttpString.tryFromString(name), value);
  }
}
