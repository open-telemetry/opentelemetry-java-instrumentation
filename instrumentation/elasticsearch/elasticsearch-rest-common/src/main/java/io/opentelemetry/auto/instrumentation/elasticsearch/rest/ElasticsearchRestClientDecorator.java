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

package io.opentelemetry.auto.instrumentation.elasticsearch.rest;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.DatabaseClientDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import org.elasticsearch.client.Response;

public class ElasticsearchRestClientDecorator extends DatabaseClientDecorator {
  public static final ElasticsearchRestClientDecorator DECORATE =
      new ElasticsearchRestClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.elasticsearch");

  @Override
  protected String dbSystem() {
    return "elasticsearch";
  }

  @Override
  protected String dbUser(final Object o) {
    return null;
  }

  @Override
  protected String dbName(final Object o) {
    return null;
  }

  public Span onRequest(final Span span, final String method, final String endpoint) {
    span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), method);
    span.setAttribute(SemanticAttributes.HTTP_URL.key(), endpoint);
    return span;
  }

  public Span onResponse(final Span span, final Response response) {
    if (response != null && response.getHost() != null) {
      setPeer(span, response.getHost().getHostName(), null);
      span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), response.getHost().getPort());
    }
    return span;
  }
}
