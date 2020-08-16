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

package io.opentelemetry.instrumentation.api.decorator;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;

/** @deprecated use {@link DatabaseClientTracer} instead. */
@Deprecated
public abstract class DatabaseClientDecorator<CONNECTION> extends ClientDecorator {

  protected abstract String dbSystem();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbName(CONNECTION connection);

  // TODO make abstract after implementing in all subclasses
  protected String dbConnectionString(CONNECTION connection) {
    return null;
  }

  @Override
  public Span afterStart(Span span) {
    assert span != null;
    span.setAttribute(SemanticAttributes.DB_SYSTEM.key(), dbSystem());
    return super.afterStart(span);
  }

  /** This should be called when the connection is being used, not when it's created. */
  public Span onConnection(Span span, CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      span.setAttribute(SemanticAttributes.DB_USER.key(), dbUser(connection));
      span.setAttribute(SemanticAttributes.DB_NAME.key(), dbName(connection));
      span.setAttribute(
          SemanticAttributes.DB_CONNECTION_STRING.key(), dbConnectionString(connection));
    }
    return span;
  }

  public Span onStatement(Span span, String statement) {
    assert span != null;
    span.setAttribute(SemanticAttributes.DB_STATEMENT.key(), statement);
    return span;
  }
}
