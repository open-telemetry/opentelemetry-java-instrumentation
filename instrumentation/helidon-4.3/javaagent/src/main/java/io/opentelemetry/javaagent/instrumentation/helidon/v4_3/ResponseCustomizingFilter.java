/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.helidon.v4_3;

import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseCustomizerHolder;

final class ResponseCustomizingFilter implements Filter {

  ResponseCustomizingFilter() {}

  @Override
  public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {

    var context = Context.current();
    HttpServerResponseCustomizerHolder.getCustomizer()
        .customize(context, res, HelidonServerResponseMutator.INSTANCE);
    chain.proceed();
  }
}
