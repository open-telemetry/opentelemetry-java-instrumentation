/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import java.net.InetSocketAddress;
import org.elasticsearch.action.Action;

public class ElasticsearchTransportClientTracer
    extends DatabaseClientTracer<Void, Action<?, ?, ?>, String> {

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty(
              "otel.instrumentation.elasticsearch.experimental-span-attributes", false);

  private static final ElasticsearchTransportClientTracer TRACER =
      new ElasticsearchTransportClientTracer();

  public static ElasticsearchTransportClientTracer tracer() {
    return TRACER;
  }

  public void onRequest(Context context, Class<?> action, Class<?> request) {
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      Span span = Span.fromContext(context);
      span.setAttribute("elasticsearch.action", action.getSimpleName());
      span.setAttribute("elasticsearch.request", request.getSimpleName());
    }
  }

  @Override
  protected String sanitizeStatement(Action<?, ?, ?> action) {
    return action.getClass().getSimpleName();
  }

  @Override
  protected String dbSystem(Void connection) {
    return "elasticsearch";
  }

  @Override
  protected InetSocketAddress peerAddress(Void connection) {
    return null;
  }

  @Override
  protected String dbOperation(Void connection, Action<?, ?, ?> action, String operation) {
    return operation;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.elasticsearch-transport-common";
  }
}
