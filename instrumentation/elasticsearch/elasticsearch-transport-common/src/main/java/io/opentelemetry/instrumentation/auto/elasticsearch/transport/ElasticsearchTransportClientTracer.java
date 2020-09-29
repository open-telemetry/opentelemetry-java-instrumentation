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

package io.opentelemetry.instrumentation.auto.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.elasticsearch.action.Action;

public class ElasticsearchTransportClientTracer
    extends DatabaseClientTracer<Void, Action<?, ?, ?>> {
  public static final ElasticsearchTransportClientTracer TRACER =
      new ElasticsearchTransportClientTracer();

  public Span onRequest(Span span, Class action, Class request) {
    span.setAttribute("elasticsearch.action", action.getSimpleName());
    span.setAttribute("elasticsearch.request", request.getSimpleName());
    return span;
  }

  @Override
  protected @NonNull String normalizeQuery(Action<?, ?, ?> query) {
    return query.getClass().getSimpleName();
  }

  @Override
  protected @NonNull String dbSystem(Void connection) {
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
    return "io.opentelemetry.auto.elasticsearch";
  }
}
