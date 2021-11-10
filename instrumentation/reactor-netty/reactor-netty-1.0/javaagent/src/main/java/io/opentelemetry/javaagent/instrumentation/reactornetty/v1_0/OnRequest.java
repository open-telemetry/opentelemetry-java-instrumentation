/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import java.util.function.BiConsumer;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientRequest;

public class OnRequest implements BiConsumer<HttpClientRequest, Connection> {
  @Override
  public void accept(HttpClientRequest r, Connection c) {
    Context context = r.currentContextView().get(MapConnect.CONTEXT_ATTRIBUTE);
    c.channel().attr(AttributeKeys.WRITE_CONTEXT).set(context);
  }
}
