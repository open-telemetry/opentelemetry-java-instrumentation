/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.datastax.cassandra;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.DatabaseClientDecorator;
import io.opentelemetry.trace.Span;

public class CassandraClientDecorator extends DatabaseClientDecorator<Session> {
  public static final CassandraClientDecorator DECORATE = new CassandraClientDecorator();

  @Override
  protected String service() {
    return "cassandra";
  }

  @Override
  protected String getComponentName() {
    return "java-cassandra";
  }

  @Override
  protected String dbType() {
    return "cassandra";
  }

  @Override
  protected String dbUser(final Session session) {
    return null;
  }

  @Override
  protected String dbInstance(final Session session) {
    return session.getLoggedKeyspace();
  }

  public Span onResponse(final Span span, final ResultSet result) {
    if (result != null) {
      final Host host = result.getExecutionInfo().getQueriedHost();
      onPeerConnection(span, host.getSocketAddress());
    }
    return span;
  }
}
