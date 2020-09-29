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

package io.opentelemetry.instrumentation.auto.geode;

import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.instrumentation.auto.api.jdbc.DbSystem;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.apache.geode.cache.Region;
import org.checkerframework.checker.nullness.qual.NonNull;

public class GeodeTracer extends DatabaseClientTracer<Region<?, ?>, String> {
  public static GeodeTracer TRACER = new GeodeTracer();

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
  protected @NonNull String normalizeQuery(String query) {
    return query;
  }

  @Override
  protected @NonNull String dbSystem(Region<?, ?> region) {
    return DbSystem.GEODE;
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
    return "io.opentelemetry.auto.geode-1.7";
  }
}
