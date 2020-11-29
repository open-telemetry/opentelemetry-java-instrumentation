/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import java.net.InetSocketAddress;
import org.elasticsearch.client.Response;

public class ElasticsearchRestClientTracer extends DatabaseClientTracer<Void, String> {
  private static final ElasticsearchRestClientTracer TRACER = new ElasticsearchRestClientTracer();

  public static ElasticsearchRestClientTracer tracer() {
    return TRACER;
  }

  public Span onRequest(Span span, String method, String endpoint) {
    span.setAttribute(SemanticAttributes.HTTP_METHOD, method);
    span.setAttribute(SemanticAttributes.HTTP_URL, endpoint);
    return span;
  }

  public Span onResponse(Span span, Response response) {
    if (response != null && response.getHost() != null) {
      NetPeerUtils.setNetPeer(span, response.getHost().getHostName(), null);
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, (long) response.getHost().getPort());
    }
    return span;
  }

  @Override
  protected void onStatement(Span span, String statement) {
    span.setAttribute(SemanticAttributes.DB_OPERATION, statement);
  }

  @Override
  protected String normalizeQuery(String query) {
    return query;
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
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.elasticsearch";
  }
}
