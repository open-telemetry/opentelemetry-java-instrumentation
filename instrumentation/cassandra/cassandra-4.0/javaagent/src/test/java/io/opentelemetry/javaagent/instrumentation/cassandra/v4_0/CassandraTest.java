/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import io.opentelemetry.cassandra.common.v4_0.AbstractCassandraTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTest extends AbstractCassandraTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.cassandra-4.0";
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void responsePeerComesFromTheChannel() {
    CqlSession session = getSession(null);
    cleanup.deferCleanup(session);

    session.execute("SELECT release_version FROM system.local");
    testing.waitForTraces(1);

    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(NETWORK_PEER_ADDRESS)).isEqualTo(cassandraIp);
              assertThat(span.getAttributes().get(NETWORK_PEER_PORT)).isEqualTo(cassandraPort);
            });
  }
}
