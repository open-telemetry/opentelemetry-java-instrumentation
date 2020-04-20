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
package io.opentelemetry.auto.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.DatabaseClientDecorator;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;
import java.util.Optional;

public class CassandraClientDecorator extends DatabaseClientDecorator<CqlSession> {
  public static final CassandraClientDecorator DECORATE = new CassandraClientDecorator();

  @Override
  protected String dbType() {
    return "cassandra";
  }

  @Override
  protected String dbUser(final CqlSession session) {
    return null;
  }

  @Override
  protected String dbInstance(final CqlSession session) {
    return session.getKeyspace().map(CqlIdentifier::toString).orElse(null);
  }

  public void onResponse(final Span span, final ExecutionInfo executionInfo) {
    if (executionInfo != null) {
      final Node coordinator = executionInfo.getCoordinator();
      if (coordinator != null) {
        final Optional<InetSocketAddress> address = coordinator.getBroadcastRpcAddress();
        address.ifPresent(inetSocketAddress -> onPeerConnection(span, inetSocketAddress));
      }
    }
  }
}
