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

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public abstract class DatabaseClientTracer<CONNECTION, QUERY> extends BaseTracer {

  private static final String DB_QUERY = "DB Query";

  protected final Tracer tracer;

  public DatabaseClientTracer() {
    tracer = OpenTelemetry.getTracer(getInstrumentationName(), getVersion());
  }

  public Span startSpan(CONNECTION connection, QUERY query) {
    String normalizedQuery = normalizeQuery(query);

    Span span =
        tracer
            .spanBuilder(spanName(normalizedQuery))
            .setSpanKind(CLIENT)
            .setAttribute(SemanticAttributes.DB_SYSTEM.key(), dbSystem(connection))
            .startSpan();

    if (connection != null) {
      onConnection(span, connection);
      onPeerConnection(span, connection);
    }
    onStatement(span, normalizedQuery);

    return span;
  }

  /**
   * Creates new scoped context with the given span.
   *
   * <p>Attaches new context to the request to avoid creating duplicate client spans.
   */
  public Scope startScope(Span span) {
    // TODO we could do this in one go, but TracingContextUtils.CONTEXT_SPAN_KEY is private
    Context clientSpanContext = Context.current().withValue(CONTEXT_CLIENT_SPAN_KEY, span);
    Context newContext = withSpan(span, clientSpanContext);
    return withScopedContext(newContext);
  }

  @Override
  public Span getCurrentSpan() {
    return tracer.getCurrentSpan();
  }

  public Span getClientSpan() {
    Context context = Context.current();
    return CONTEXT_CLIENT_SPAN_KEY.get(context);
  }

  // TODO make abstract when implemented in all subclasses
  @Override
  protected String getInstrumentationName() {
    return null;
  }

  @Override
  public void end(Span span) {
    span.end();
  }

  @Override
  public void endExceptionally(Span span, Throwable throwable) {
    onError(span, throwable);
    end(span);
  }

  /** This should be called when the connection is being used, not when it's created. */
  protected Span onConnection(final Span span, final CONNECTION connection) {
    span.setAttribute(SemanticAttributes.DB_USER.key(), dbUser(connection));
    span.setAttribute(SemanticAttributes.DB_NAME.key(), dbName(connection));
    span.setAttribute(
        SemanticAttributes.DB_CONNECTION_STRING.key(), dbConnectionString(connection));
    return span;
  }

  @Override
  protected void onError(final Span span, final Throwable throwable) {
    if (throwable != null) {
      span.setStatus(Status.UNKNOWN);
      addThrowable(
          span, throwable instanceof ExecutionException ? throwable.getCause() : throwable);
    }
  }

  protected void onPeerConnection(final Span span, final CONNECTION connection) {
    onPeerConnection(span, peerAddress(connection));
  }

  protected void onStatement(final Span span, final String statement) {
    span.setAttribute(SemanticAttributes.DB_STATEMENT.key(), statement);
  }

  // TODO: "When it's impossible to get any meaningful representation of the span name, it can be
  // populated using the same value as db.name" (c) spec
  protected String spanName(final String query) {
    return query == null ? DB_QUERY : query;
  }

  protected abstract String normalizeQuery(QUERY query);

  protected abstract String dbSystem(CONNECTION connection);

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbName(CONNECTION connection);

  // TODO make abstract after implementing in all subclasses
  protected String dbConnectionString(final CONNECTION connection) {
    return null;
  }

  protected abstract InetSocketAddress peerAddress(CONNECTION connection);
}
