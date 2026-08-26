/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.cassandra.v4_4.AbstractCassandra44Test;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTest extends AbstractCassandra44Test {

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.cassandra-4.4";
  }

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void sniNetworkPeerIsTheResponseChannelProxy() {
    InetSocketAddress proxyAddress = new InetSocketAddress(cassandraHost, cassandraPort);
    CqlSession session =
        CqlSession.builder()
            .addContactEndPoint(new SniEndPoint(proxyAddress, "localhost"))
            .withLocalDatacenter("datacenter1")
            .build();
    cleanup.deferCleanup(session);

    session.execute("SELECT release_version FROM system.local");
    testing.waitForTraces(1);

    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              String peerAddress = span.getAttributes().get(NETWORK_PEER_ADDRESS);
              assertThat(peerAddress).isIn("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
              assertThat(span.getAttributes().get(NETWORK_PEER_PORT)).isEqualTo(cassandraPort);
              if (emitStableDatabaseSemconv()) {
                assertThat(span.getAttributes().get(SERVER_ADDRESS)).isEqualTo("0.0.0.0");
                assertThat(span.getAttributes().get(SERVER_PORT)).isEqualTo(9042L);
              }
            });
  }
}
