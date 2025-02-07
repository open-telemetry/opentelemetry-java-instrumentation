/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpserver;

import java.io.IOException;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;

class ResponseCustomizingFilter extends Filter {

  ResponseCustomizingFilter() {}

  @Override
  public void doFilter(HttpExchange exchange, Chain chain) throws IOException {

    Context context = Context.current();
    HttpServerResponseCustomizerHolder.getCustomizer()
        .customize(context, exchange.getResponseHeaders(), JavaHttpResponseMutator.INSTANCE);
    chain.doFilter(exchange);
  }

  @Override
  public String description() {
    return "ResponseCustomizingFilter";
  }
}
