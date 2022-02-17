/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/database.md">database
 * attributes</a>. Instrumentations of database libraries should extend this class, defining {@link
 * REQUEST} with the actual request type of the instrumented library. If an attribute is not
 * available in this library, it is appropriate to return {@code null} from the protected attribute
 * methods, but implement as many as possible for best compliance with the OpenTelemetry
 * specification.
 */
public abstract class DbAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    set(attributes, SemanticAttributes.DB_SYSTEM, system(request));
    set(attributes, SemanticAttributes.DB_USER, user(request));
    set(attributes, SemanticAttributes.DB_NAME, name(request));
    set(attributes, SemanticAttributes.DB_CONNECTION_STRING, connectionString(request));
    set(attributes, SemanticAttributes.DB_STATEMENT, statement(request));
    set(attributes, SemanticAttributes.DB_OPERATION, operation(request));
  }

  @Override
  public final void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}

  @Nullable
  protected abstract String system(REQUEST request);

  @Nullable
  protected abstract String user(REQUEST request);

  @Nullable
  protected abstract String name(REQUEST request);

  @Nullable
  protected abstract String connectionString(REQUEST request);

  @Nullable
  protected abstract String statement(REQUEST request);

  @Nullable
  protected abstract String operation(REQUEST request);
}
