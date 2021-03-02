/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.apache.geode.cache.Region;

public class GeodeTracer extends DatabaseClientTracer<Region<?, ?>, String, SqlStatementInfo> {
  private static final GeodeTracer TRACER = new GeodeTracer();

  public static GeodeTracer tracer() {
    return TRACER;
  }

  public Context startSpan(String operation, Region<?, ?> connection, String query) {
    SqlStatementInfo sanitizedStatement = sanitizeStatement(query);

    SpanBuilder span =
        tracer
            .spanBuilder(operation)
            .setSpanKind(CLIENT)
            .setAttribute(SemanticAttributes.DB_SYSTEM, dbSystem(connection))
            .setAttribute(SemanticAttributes.DB_OPERATION, operation);

    onConnection(span, connection);
    setNetSemanticConvention(span, connection);
    onStatement(span, connection, query, sanitizedStatement);

    return Context.current().with(span.startSpan());
  }

  @Override
  protected SqlStatementInfo sanitizeStatement(String statement) {
    return SqlStatementSanitizer.sanitize(statement);
  }

  @Override
  protected String dbSystem(Region<?, ?> region) {
    return SemanticAttributes.DbSystemValues.GEODE;
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
  protected String dbStatement(
      Region<?, ?> connection, String statement, SqlStatementInfo sanitizedStatement) {
    return sanitizedStatement.getFullStatement();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.geode-1.4";
  }
}
