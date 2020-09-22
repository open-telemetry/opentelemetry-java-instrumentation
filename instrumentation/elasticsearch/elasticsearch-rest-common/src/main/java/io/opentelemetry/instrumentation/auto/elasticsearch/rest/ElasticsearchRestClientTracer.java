/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.elasticsearch.rest;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.elasticsearch.client.Response;

public class ElasticsearchRestClientTracer extends DatabaseClientTracer<String, String> {
  public static final ElasticsearchRestClientTracer TRACER = new ElasticsearchRestClientTracer();

  public Span onRequest(Span span, String method, String endpoint) {
    span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), method);
    span.setAttribute(SemanticAttributes.HTTP_URL.key(), endpoint);
    return span;
  }

  public Span onResponse(Span span, Response response) {
    if (response != null && response.getHost() != null) {
      NetPeerUtils.setNetPeer(span, response.getHost().getHostName(), null);
      span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), response.getHost().getPort());
    }
    return span;
  }

  @Override
  protected void onStatement(Span span, String statement) {
    SemanticAttributes.DB_OPERATION.set(span, statement);
  }

  @Override
  protected String normalizeQuery(String query) {
    return query;
  }

  @Override
  protected String dbSystem(String s) {
    return "elasticsearch";
  }

  @Override
  protected String dbUser(String s) {
    return null;
  }

  @Override
  protected String dbName(String s) {
    return null;
  }

  @Override
  protected InetSocketAddress peerAddress(String s) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.elasticsearch";
  }
}
