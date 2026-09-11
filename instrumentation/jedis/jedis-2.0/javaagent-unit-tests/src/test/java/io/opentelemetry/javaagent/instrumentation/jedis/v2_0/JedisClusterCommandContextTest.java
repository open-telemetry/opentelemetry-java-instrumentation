/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.context.Context;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;

class JedisClusterCommandContextTest {

  @Test
  void ignoresConnectionAcquisitionCommands() {
    assumeTrue(emitStableDatabaseSemconv());

    JedisClusterCommandContext commandContext = JedisClusterCommandContext.start();
    JedisRequest pingRequest =
        requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379));
    pingRequest.capturePeerAddress();

    commandContext.capture(Context.root(), pingRequest);

    assertThat(commandContext.hasRequest()).isFalse();

    commandContext.enterExecute();
    try {
      commandContext.capture(
          Context.root(),
          requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380)));
    } finally {
      commandContext.exitExecute();
    }

    assertThat(commandContext.hasRequest()).isTrue();
    commandContext.end(null);
  }

  @Test
  void tracksNestedConnectionAcquisition() {
    assumeTrue(emitStableDatabaseSemconv());

    JedisClusterCommandContext commandContext = JedisClusterCommandContext.start();
    try {
      assertThat(commandContext.isAcquiringConnection()).isFalse();

      // getting a connection for a slot delegates to getting a connection, so the acquisitions nest
      JedisClusterCommandContext.enterConnectionAcquisition();
      JedisClusterCommandContext.enterConnectionAcquisition();
      assertThat(commandContext.isAcquiringConnection()).isTrue();

      JedisClusterCommandContext.exitConnectionAcquisition();
      assertThat(commandContext.isAcquiringConnection()).isTrue();

      JedisClusterCommandContext.exitConnectionAcquisition();
      assertThat(commandContext.isAcquiringConnection()).isFalse();
    } finally {
      commandContext.end(null);
    }
  }

  @Test
  void connectionAcquisitionSuppressesOnlyHealthCheck() {
    assumeTrue(emitStableDatabaseSemconv());

    Connection connection = new Connection();
    JedisClusterCommandContext commandContext = JedisClusterCommandContext.start();
    try {
      JedisClusterCommandContext.enterConnectionAcquisition();

      assertThat(
              JedisConnectionInstrumentation.SendCommandNoArgsAdvice.onEnter(
                  connection, Protocol.Command.PING))
          .isNull();

      // jedis 2.0.0 has no CLUSTER command constant, so an ordinary command stands in for the slot
      // cache refresh a missing slot triggers while a connection is being borrowed
      JedisConnectionInstrumentation.AdviceScope refreshScope =
          JedisConnectionInstrumentation.SendCommandNoArgsAdvice.onEnter(
              connection, Protocol.Command.GET);
      assertThat(refreshScope).isNotNull();
      JedisConnectionInstrumentation.SendCommandNoArgsAdvice.stopSpan(null, refreshScope);
    } finally {
      JedisClusterCommandContext.exitConnectionAcquisition();
      commandContext.end(null);
    }
  }

  @Test
  void matchesOnlyCapturedRequest() {
    assumeTrue(emitStableDatabaseSemconv());

    Connection connection = new Connection();
    JedisClusterCommandContext commandContext = JedisClusterCommandContext.start();
    commandContext.enterExecute();
    try {
      commandContext.capture(
          Context.root(),
          JedisRequest.create(
              connection, Protocol.Command.GET, singletonList("key".getBytes(US_ASCII))));

      assertThat(
              commandContext.matchesCapturedRequest(
                  JedisRequest.create(
                      connection, Protocol.Command.GET, singletonList("key".getBytes(US_ASCII)))))
          .isTrue();
      assertThat(
              commandContext.matchesCapturedRequest(
                  JedisRequest.create(
                      connection, Protocol.Command.GET, singletonList("other".getBytes(US_ASCII)))))
          .isFalse();
      assertThat(
              commandContext.matchesCapturedRequest(
                  JedisRequest.create(
                      connection, Protocol.Command.SET, singletonList("key".getBytes(US_ASCII)))))
          .isFalse();
    } finally {
      commandContext.exitExecute();
      commandContext.end(null);
    }
  }

  private static JedisRequest requestWithPeer(InetSocketAddress peerAddress) {
    Socket socket =
        new Socket() {
          @Override
          public SocketAddress getRemoteSocketAddress() {
            return peerAddress;
          }

          @Override
          public boolean isConnected() {
            return true;
          }
        };
    Connection connection =
        new Connection() {
          @Override
          public Socket getSocket() {
            return socket;
          }
        };
    return JedisRequest.create(connection, Protocol.Command.GET);
  }
}
