/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.apache.geode.cache.Region;

public class GeodeTracer extends DatabaseClientTracer<Region<?, ?>, String> {
  private static final GeodeTracer TRACER = new GeodeTracer();

  public static GeodeTracer tracer() {
    return TRACER;
  }

  public Span startSpan(String operation, Region<?, ?> connection, String query) {
    String normalizedQuery = normalizeQuery(query);

    Span span =
        tracer
            .spanBuilder(operation)
            .setSpanKind(CLIENT)
            .setAttribute(SemanticAttributes.DB_SYSTEM, dbSystem(connection))
            .setAttribute(SemanticAttributes.DB_OPERATION, operation)
            .startSpan();

    onConnection(span, connection);
    setNetSemanticConvention(span, connection);
    onStatement(span, normalizedQuery);

    return span;
  }

  @Override
  protected String normalizeQuery(String query) {
    return SqlStatementSanitizer.sanitize(query).getFullStatement();
  }

  @Override
  protected String dbSystem(Region<?, ?> region) {
    // TODO(anuraaga): Replace with semantic attribute
    // https://github.com/open-telemetry/opentelemetry-specification/pull/1321
    return "geode";
  }

  @Override
  protected String dbName(Region<?, ?> region) {
    return region.getName();
  }

  @Override
  protected InetSocketAddress peerAddress(Region<?, ?> region) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.geode";
  }
}
