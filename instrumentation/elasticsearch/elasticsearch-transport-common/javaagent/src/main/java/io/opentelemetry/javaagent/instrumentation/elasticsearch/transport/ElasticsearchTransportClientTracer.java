/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.DatabaseClientInstrumenter;
import java.net.InetSocketAddress;
import org.elasticsearch.action.Action;

public class ElasticsearchTransportClientTracer
    extends DatabaseClientInstrumenter<Void, Action<?, ?, ?>> {
  private static final ElasticsearchTransportClientTracer TRACER =
      new ElasticsearchTransportClientTracer();

  public static ElasticsearchTransportClientTracer tracer() {
    return TRACER;
  }

  public void onRequest(Context context, Class action, Class request) {
    Span span =
        io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext(context);
    span.setAttribute("elasticsearch.action", action.getSimpleName());
    span.setAttribute("elasticsearch.request", request.getSimpleName());
  }

  @Override
  protected String normalizeQuery(Action<?, ?, ?> query) {
    return query.getClass().getSimpleName();
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
  protected void onStatement(Span span, String statement) {
    span.setAttribute(SemanticAttributes.DB_OPERATION, statement);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.elasticsearch";
  }
}
