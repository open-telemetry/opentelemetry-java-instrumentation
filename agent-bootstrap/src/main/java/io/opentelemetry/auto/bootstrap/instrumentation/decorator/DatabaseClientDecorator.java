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
package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.net.InetSocketAddress;

public abstract class DatabaseClientDecorator<CONNECTION, QUERY> extends ClientDecorator {
  private static final String DB_QUERY = "DB Query";

  protected final Tracer tracer;

  public DatabaseClientDecorator() {
    tracer = OpenTelemetry.getTracerProvider().get(getInstrumentationName(), getVersion());
  }

  // TODO make abstract when implemented in all subclasses
  protected String getInstrumentationName() {
    return null;
  }

  private String getVersion() {
    return null;
  }

  public Scope withSpan(Span span) {
    return tracer.withSpan(span);
  }

  public Span startSpan(CONNECTION connection, QUERY query, String originType) {
    String normalizedQuery = normalizeQuery(query);

    final Span span =
        tracer
            .spanBuilder(spanName(normalizedQuery))
            .setSpanKind(CLIENT)
            .setAttribute(Tags.DB_TYPE, dbType())
            .setAttribute("span.origin.type", originType)
            .startSpan();

    onConnection(span, connection);
    onPeerConnection(span, connection);
    onStatement(span, normalizedQuery);

    return span;
  }

  public void end(Span span) {
    span.end();
  }

  public void endExceptionally(Span span, Throwable throwable) {
    onError(span, throwable);
    end(span);
  }

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    span.setAttribute(Tags.DB_TYPE, dbType());
    return super.afterStart(span);
  }

  /** This should be called when the connection is being used, not when it's created. */
  public Span onConnection(final Span span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      span.setAttribute(Tags.DB_USER, dbUser(connection));
      span.setAttribute(Tags.DB_INSTANCE, dbInstance(connection));
      span.setAttribute(Tags.DB_URL, dbUrl(connection));
    }
    return span;
  }

  protected void onPeerConnection(Span span, final CONNECTION connection) {
    onPeerConnection(span, peerAddress(connection));
  }

  public Span onStatement(final Span span, final String statement) {
    assert span != null;
    span.setAttribute(Tags.DB_STATEMENT, statement);
    return span;
  }
  // TODO: "When it's impossible to get any meaningful representation of the span name, it can be
  // populated using the same value as db.instance" (c) spec

  protected String spanName(final String query) {
    return query == null ? DB_QUERY : query;
  }

  protected abstract String normalizeQuery(QUERY query);

  protected abstract String dbType();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbInstance(CONNECTION connection);

  protected abstract InetSocketAddress peerAddress(CONNECTION connection);
  // TODO make abstract after implementing in all subclasses

  protected String dbUrl(final CONNECTION connection) {
    return null;
  }
}
