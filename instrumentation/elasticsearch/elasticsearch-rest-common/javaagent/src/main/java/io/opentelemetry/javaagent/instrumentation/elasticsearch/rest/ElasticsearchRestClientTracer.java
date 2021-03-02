/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.elasticsearch.client.Response;

public class ElasticsearchRestClientTracer extends DatabaseClientTracer<Void, String, String> {
  private static final ElasticsearchRestClientTracer TRACER = new ElasticsearchRestClientTracer();

  public static ElasticsearchRestClientTracer tracer() {
    return TRACER;
  }

  public void onResponse(Context context, Response response) {
    if (response != null && response.getHost() != null) {
      Span span = Span.fromContext(context);
      NetPeerUtils.INSTANCE.setNetPeer(span, response.getHost().getHostName(), null);
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, (long) response.getHost().getPort());
    }
  }

  @Override
  protected String sanitizeStatement(String operation) {
    return operation;
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
  protected String dbOperation(Void connection, String operation, String ignored) {
    return operation;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.elasticsearch-rest-common";
  }
}
