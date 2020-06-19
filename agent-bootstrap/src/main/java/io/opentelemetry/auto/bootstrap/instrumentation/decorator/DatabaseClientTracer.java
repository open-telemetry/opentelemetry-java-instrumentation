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
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

public abstract class DatabaseClientTracer<CONNECTION, QUERY> {
  private static final String DB_QUERY = "DB Query";

  protected final Tracer tracer;

  public DatabaseClientTracer() {
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

  protected Span afterStart(final Span span) {
    assert span != null;
    span.setAttribute(Tags.DB_TYPE, dbType());
    return span;
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

  public Span onError(final Span span, final Throwable throwable) {
    assert span != null;
    if (throwable != null) {
      span.setStatus(Status.UNKNOWN);
      addThrowable(
          span, throwable instanceof ExecutionException ? throwable.getCause() : throwable);
    }
    return span;
  }

  public static void addThrowable(final Span span, final Throwable throwable) {
    span.setAttribute(MoreTags.ERROR_MSG, throwable.getMessage());
    span.setAttribute(MoreTags.ERROR_TYPE, throwable.getClass().getName());

    final StringWriter errorString = new StringWriter();
    throwable.printStackTrace(new PrintWriter(errorString));
    span.setAttribute(MoreTags.ERROR_STACK, errorString.toString());
  }

  protected void onPeerConnection(Span span, final CONNECTION connection) {
    onPeerConnection(span, peerAddress(connection));
  }

  protected void onPeerConnection(final Span span, final InetSocketAddress remoteConnection) {
    assert span != null;
    if (remoteConnection != null) {
      onPeerConnection(span, remoteConnection.getAddress());

      span.setAttribute(MoreTags.NET_PEER_PORT, remoteConnection.getPort());
    }
  }

  protected void onPeerConnection(final Span span, final InetAddress remoteAddress) {
    assert span != null;
    if (remoteAddress != null) {
      span.setAttribute(MoreTags.NET_PEER_NAME, remoteAddress.getHostName());
      span.setAttribute(MoreTags.NET_PEER_IP, remoteAddress.getHostAddress());
    }
  }

  public void onStatement(final Span span, final String statement) {
    assert span != null;
    span.setAttribute(Tags.DB_STATEMENT, statement);
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

  // TODO make abstract after implementing in all subclasses
  protected String dbUrl(final CONNECTION connection) {
    return null;
  }

  protected abstract InetSocketAddress peerAddress(CONNECTION connection);
}
