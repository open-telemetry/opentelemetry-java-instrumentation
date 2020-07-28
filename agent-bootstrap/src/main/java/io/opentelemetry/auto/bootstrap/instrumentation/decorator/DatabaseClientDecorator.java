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

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import io.opentelemetry.trace.attributes.StringAttributeSetter;

/** @deprecated use {@link DatabaseClientTracer} instead. */
@Deprecated
public abstract class DatabaseClientDecorator<CONNECTION> extends ClientDecorator {

  protected abstract String dbSystem();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbName(CONNECTION connection);

  // TODO make abstract after implementing in all subclasses
  protected String dbConnectionString(final CONNECTION connection) {
    return null;
  }

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    span.setAttribute(StringAttributeSetter.create("db.system").key(), dbSystem());
    return super.afterStart(span);
  }

  /** This should be called when the connection is being used, not when it's created. */
  public Span onConnection(final Span span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      span.setAttribute(SemanticAttributes.DB_USER.key(), dbUser(connection));
      span.setAttribute(StringAttributeSetter.create("db.name").key(), dbName(connection));
      span.setAttribute(
          StringAttributeSetter.create("db.connection_string").key(),
          dbConnectionString(connection));
    }
    return span;
  }

  public Span onStatement(final Span span, final String statement) {
    assert span != null;
    span.setAttribute(SemanticAttributes.DB_STATEMENT.key(), statement);
    return span;
  }
}
