/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisClusterCommandContext.currentCommandContext;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.currentBatch;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.currentTransactionFraming;
import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.transactionFraming;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisClusterCommandInstrumentation.CommandAdvice.AdviceState;
import io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisPipelineContext.TransactionFraming;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.Queable;
import redis.clients.jedis.Transaction;

class JedisNetworkAttributesGetterTest {

  @ParameterizedTest
  @MethodSource("resolvedAddresses")
  void usesConnectedInetSocketAddress(InetAddress address) {
    InetSocketAddress peerAddress = new InetSocketAddress(address, 6379);
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
    JedisRequest request = JedisRequest.create(connection, Protocol.Command.GET);
    request.capturePeerAddress();

    assertThat(request.getPeerAddress()).isEqualTo(peerAddress);
  }

  @Test
  void emitsConnectedSocketAddressOnlyForStableSemconv() {
    InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
    JedisRequest request = requestWithPeer(peerAddress);
    request.capturePeerAddress();

    assertThat(new JedisDbAttributesGetter().getNetworkPeerInetSocketAddress(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? peerAddress : null);
  }

  @Test
  void dropsMissingSocketAddress() {
    JedisRequest request = JedisRequest.create(new Connection(), Protocol.Command.GET);
    request.capturePeerAddress();

    assertThat(request.getPeerAddress()).isNull();
  }

  @Test
  void dropsUnresolvedSocketAddress() {
    InetSocketAddress peerAddress = InetSocketAddress.createUnresolved("redis.example", 6379);
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
    JedisRequest request = JedisRequest.create(connection, Protocol.Command.GET);
    request.capturePeerAddress();

    assertThat(request.getPeerAddress()).isNull();
  }

  @Test
  void dropsClosedSocketAddress() {
    InetSocketAddress peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
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

          @Override
          public boolean isClosed() {
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
    JedisRequest request = JedisRequest.create(connection, Protocol.Command.GET);
    request.capturePeerAddress();

    assertThat(request.getPeerAddress()).isNull();
  }

  @Test
  void laterCaptureReplacesEarlierPeerAddress() {
    InetSocketAddress first = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
    InetSocketAddress second = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380);
    Socket socket =
        new Socket() {
          private SocketAddress address = first;

          @Override
          public SocketAddress getRemoteSocketAddress() {
            SocketAddress current = address;
            address = second;
            return current;
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
    JedisRequest request = JedisRequest.create(connection, Protocol.Command.GET);
    request.capturePeerAddress();
    request.capturePeerAddress();

    assertThat(request.getPeerAddress()).isEqualTo(second);
  }

  @Test
  void clusterContextIgnoresCommandsOutsideExecute() {
    assumeTrue(emitStableDatabaseSemconv());

    JedisClusterCommandContext commandContext = JedisClusterCommandContext.create();
    JedisClusterCommandContext previous = currentCommandContext().set(commandContext);
    try {
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
    } finally {
      try {
        commandContext.end(null);
      } finally {
        currentCommandContext().restore(previous);
      }
    }
  }

  @Test
  void clusterContextTracksNestedConnectionAcquisition() {
    assumeTrue(emitStableDatabaseSemconv());

    JedisClusterCommandContext commandContext = JedisClusterCommandContext.create();
    JedisClusterCommandContext previous = currentCommandContext().set(commandContext);
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
      try {
        commandContext.end(null);
      } finally {
        currentCommandContext().restore(previous);
      }
    }
  }

  @Test
  void clusterContextRejectsNestingWithoutClearingOuterContext() {
    assumeTrue(emitStableDatabaseSemconv());

    AdviceState outerState = JedisClusterCommandInstrumentation.CommandAdvice.onEnter();
    assertThat(outerState).isNotNull();
    JedisClusterCommandContext outer = currentCommandContext().get();
    try {
      assertThat(JedisClusterCommandInstrumentation.CommandAdvice.onEnter()).isNull();
      assertThat(currentCommandContext().get()).isSameAs(outer);
    } finally {
      JedisClusterCommandInstrumentation.CommandAdvice.onExit(null, outerState);
    }

    assertThat(currentCommandContext().get()).isNull();
  }

  @Test
  void clusterContextTracksNestedExecuteCalls() {
    assumeTrue(emitStableDatabaseSemconv());

    JedisClusterCommandContext commandContext = JedisClusterCommandContext.create();
    JedisClusterCommandContext previous = currentCommandContext().set(commandContext);
    try {
      commandContext.enterExecute();
      commandContext.enterExecute();
      commandContext.exitExecute();

      assertThat(commandContext.isExecuting()).isTrue();
      commandContext.capture(
          Context.root(),
          requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379)));
      assertThat(commandContext.hasRequest()).isTrue();

      commandContext.exitExecute();
      assertThat(commandContext.isExecuting()).isFalse();
    } finally {
      try {
        commandContext.end(null);
      } finally {
        currentCommandContext().restore(previous);
      }
    }
  }

  @Test
  void connectionAcquisitionSuppressesOnlyHealthCheck() {
    assumeTrue(emitStableDatabaseSemconv());

    Connection connection = new Connection();
    JedisClusterCommandContext commandContext = JedisClusterCommandContext.create();
    JedisClusterCommandContext previous = currentCommandContext().set(commandContext);
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
      try {
        commandContext.end(null);
      } finally {
        currentCommandContext().restore(previous);
      }
    }
  }

  @Test
  void clusterContextMatchesOnlyCapturedRequest() {
    assumeTrue(emitStableDatabaseSemconv());

    Connection connection = new Connection();
    JedisClusterCommandContext commandContext = JedisClusterCommandContext.create();
    JedisClusterCommandContext previous = currentCommandContext().set(commandContext);
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
      try {
        commandContext.end(null);
      } finally {
        currentCommandContext().restore(previous);
      }
    }
  }

  @Test
  void failedPipelineSendUsesConnectedPeerAddress() {
    InetSocketAddress first = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
    InetSocketAddress second = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380);
    MutableConnection connection = new MutableConnection(connectedSocket(first));
    Pipeline pipeline = new Pipeline();

    Queable previous = currentBatch().set(pipeline);
    try {
      JedisConnectionInstrumentation.AdviceScope firstScope =
          JedisConnectionInstrumentation.SendCommandNoArgsAdvice.onEnter(
              connection, Protocol.Command.GET);
      JedisConnectionInstrumentation.SendCommandNoArgsAdvice.stopSpan(null, firstScope);

      connection.setSocket(connectedSocket(second));
      JedisConnectionInstrumentation.AdviceScope failedScope =
          JedisConnectionInstrumentation.SendCommandNoArgsAdvice.onEnter(
              connection, Protocol.Command.GET);
      JedisConnectionInstrumentation.SendCommandNoArgsAdvice.stopSpan(
          new RuntimeException("send failed"), failedScope);
    } finally {
      currentBatch().restore(previous);
    }

    List<JedisRequest> requests = JedisPipelineContext.getAndClearCapturedRequests(pipeline);
    assertThat(JedisRequest.createPipeline(requests).getPeerAddress()).isEqualTo(second);
  }

  @Test
  void nestedPipelineCaptureRestoresOuterBatch() {
    Pipeline outer = new Pipeline();
    Pipeline inner = new Pipeline();
    JedisRequest outerFirst = JedisRequest.create(new Connection(), Protocol.Command.GET);
    JedisRequest innerRequest = JedisRequest.create(new Connection(), Protocol.Command.SET);
    JedisRequest outerSecond = JedisRequest.create(new Connection(), Protocol.Command.DEL);

    Queable outerPrevious = currentBatch().set(outer);
    try {
      assertThat(JedisPipelineContext.capture(outerFirst)).isTrue();

      Queable innerPrevious = currentBatch().set(inner);
      try {
        assertThat(JedisPipelineContext.capture(innerRequest)).isTrue();
      } finally {
        currentBatch().restore(innerPrevious);
      }

      assertThat(currentBatch().get()).isSameAs(outer);
      assertThat(JedisPipelineContext.capture(outerSecond)).isTrue();
    } finally {
      currentBatch().restore(outerPrevious);
    }

    assertThat(JedisPipelineContext.getAndClearCapturedRequests(outer))
        .containsExactly(outerFirst, outerSecond);
    assertThat(JedisPipelineContext.getAndClearCapturedRequests(inner))
        .containsExactly(innerRequest);
    assertThat(currentBatch().get()).isNull();
    assertThat(
            JedisPipelineContext.capture(
                JedisRequest.create(new Connection(), Protocol.Command.GET)))
        .isFalse();
  }

  @ParameterizedTest
  @MethodSource("unreliableSockets")
  void failedTransactionSendKeepsEarlierPeerAddress(Socket unreliableSocket) {
    InetSocketAddress first = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
    JedisRequest queuedRequest = requestWithPeer(first);
    queuedRequest.capturePeerAddress();
    JedisRequest transactionRequest =
        JedisRequest.createTransaction(singletonList(queuedRequest), null);
    MutableConnection connection = new MutableConnection(unreliableSocket);

    TransactionFraming previous =
        currentTransactionFraming().set(transactionFraming(transactionRequest));
    try {
      JedisConnectionInstrumentation.AdviceScope failedScope =
          JedisConnectionInstrumentation.SendCommandNoArgsAdvice.onEnter(
              connection, Protocol.Command.EXEC);
      JedisConnectionInstrumentation.SendCommandNoArgsAdvice.stopSpan(
          new RuntimeException("send failed"), failedScope);
    } finally {
      currentTransactionFraming().restore(previous);
    }

    assertThat(transactionRequest.getPeerAddress()).isEqualTo(first);
  }

  @Test
  void ordinaryCommandDoesNotBecomeTransactionFramingRequest() {
    JedisRequest request =
        requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379));
    request.capturePeerAddress();
    Transaction transaction = new Transaction();

    JedisPipelineContext.captureTransactionFramingPeer(request);
    JedisPipelineContext.captureTransactionFramingRequest(transaction);

    assertThat(JedisPipelineContext.getAndClearTransactionFramingRequest(transaction)).isNull();
  }

  @Test
  void pipelineUsesLastSuccessfullyObservedPeerAddress() {
    JedisRequest first =
        requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379));
    first.capturePeerAddress();
    JedisRequest second =
        requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380));
    second.capturePeerAddress();

    JedisRequest pipelineRequest = JedisRequest.createPipeline(asList(first, second));

    assertThat(pipelineRequest.getPeerAddress()).isEqualTo(second.getPeerAddress());
  }

  @Test
  void pipelineKeepsEarlierPeerWhenLaterSendHasNoReliableAddress() {
    JedisRequest first =
        requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379));
    first.capturePeerAddress();
    JedisRequest failed =
        requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380));

    JedisRequest pipelineRequest = JedisRequest.createPipeline(asList(first, failed));

    assertThat(pipelineRequest.getPeerAddress()).isEqualTo(first.getPeerAddress());
  }

  @Test
  void transactionUsesExecPeerAddress() {
    InetSocketAddress queuedPeer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
    JedisRequest queuedRequest = requestWithPeer(queuedPeer);
    queuedRequest.capturePeerAddress();
    JedisRequest multiRequest = requestWithPeer(queuedPeer);
    multiRequest.capturePeerAddress();
    JedisRequest transactionRequest =
        JedisRequest.createTransaction(singletonList(queuedRequest), multiRequest);

    InetSocketAddress execPeer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380);
    JedisRequest execRequest = requestWithPeer(execPeer);

    TransactionFraming previous =
        currentTransactionFraming().set(transactionFraming(transactionRequest));
    try {
      execRequest.capturePeerAddress();
      JedisPipelineContext.captureTransactionFramingPeer(execRequest);
    } finally {
      currentTransactionFraming().restore(previous);
    }

    assertThat(transactionRequest.getPeerAddress()).isEqualTo(execPeer);
  }

  @Test
  void nestedTransactionFramingRestoresOuterRequest() {
    InetSocketAddress outerPeer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
    JedisRequest outerQueuedRequest = requestWithPeer(outerPeer);
    JedisRequest outerTransactionRequest =
        JedisRequest.createTransaction(singletonList(outerQueuedRequest), null);
    JedisRequest outerExecRequest = requestWithPeer(outerPeer);
    outerExecRequest.capturePeerAddress();

    InetSocketAddress innerPeer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380);
    JedisRequest innerQueuedRequest = requestWithPeer(innerPeer);
    JedisRequest innerTransactionRequest =
        JedisRequest.createTransaction(singletonList(innerQueuedRequest), null);
    JedisRequest innerExecRequest = requestWithPeer(innerPeer);
    innerExecRequest.capturePeerAddress();

    TransactionFraming outerFraming = transactionFraming(outerTransactionRequest);
    TransactionFraming outerPrevious = currentTransactionFraming().set(outerFraming);
    try {
      TransactionFraming innerPrevious =
          currentTransactionFraming().set(transactionFraming(innerTransactionRequest));
      try {
        JedisPipelineContext.captureTransactionFramingPeer(innerExecRequest);
      } finally {
        currentTransactionFraming().restore(innerPrevious);
      }

      assertThat(currentTransactionFraming().get()).isSameAs(outerFraming);
      JedisPipelineContext.captureTransactionFramingPeer(outerExecRequest);
    } finally {
      currentTransactionFraming().restore(outerPrevious);
    }

    assertThat(outerTransactionRequest.getPeerAddress()).isEqualTo(outerPeer);
    assertThat(innerTransactionRequest.getPeerAddress()).isEqualTo(innerPeer);
    assertThat(JedisPipelineContext.inTransactionFraming()).isFalse();
  }

  @Test
  void transactionKeepsLastSuccessfulPeerWhenExecFails() {
    InetSocketAddress queuedPeer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
    JedisRequest queuedRequest = requestWithPeer(queuedPeer);
    queuedRequest.capturePeerAddress();
    JedisRequest multiRequest = requestWithPeer(queuedPeer);
    multiRequest.capturePeerAddress();
    JedisRequest transactionRequest =
        JedisRequest.createTransaction(singletonList(queuedRequest), multiRequest);

    JedisRequest execRequest = requestWithPeer(queuedPeer);

    TransactionFraming previous =
        currentTransactionFraming().set(transactionFraming(transactionRequest));
    try {
      JedisPipelineContext.captureTransactionFramingPeer(execRequest);
    } finally {
      currentTransactionFraming().restore(previous);
    }

    assertThat(transactionRequest.getPeerAddress()).isEqualTo(queuedPeer);
  }

  @Test
  void transactionUsesLastQueuedPeerAfterMulti() {
    InetSocketAddress queuedPeer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379);
    JedisRequest queuedRequest = requestWithPeer(queuedPeer);
    queuedRequest.capturePeerAddress();

    InetSocketAddress multiPeer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380);
    JedisRequest multiRequest = requestWithPeer(multiPeer);
    multiRequest.capturePeerAddress();

    JedisRequest transactionRequest =
        JedisRequest.createTransaction(singletonList(queuedRequest), multiRequest);

    assertThat(transactionRequest.getPeerAddress()).isEqualTo(queuedPeer);
  }

  private static JedisRequest requestWithPeer(InetSocketAddress peerAddress) {
    return JedisRequest.create(
        new MutableConnection(connectedSocket(peerAddress)), Protocol.Command.GET);
  }

  private static Socket connectedSocket(InetSocketAddress peerAddress) {
    return new Socket() {
      @Override
      public SocketAddress getRemoteSocketAddress() {
        return peerAddress;
      }

      @Override
      public boolean isConnected() {
        return true;
      }
    };
  }

  private static Stream<Socket> unreliableSockets() {
    Socket unconnectedSocket =
        new Socket() {
          @Override
          public SocketAddress getRemoteSocketAddress() {
            return new InetSocketAddress(InetAddress.getLoopbackAddress(), 6380);
          }
        };
    return Stream.of(null, unconnectedSocket);
  }

  private static Stream<InetAddress> resolvedAddresses() throws UnknownHostException {
    return Stream.of(
        InetAddress.getByAddress(new byte[] {127, 0, 0, 1}),
        InetAddress.getByAddress(new byte[16]));
  }

  private static class MutableConnection extends Connection {
    private Socket socket;

    private MutableConnection(Socket socket) {
      this.socket = socket;
    }

    @Override
    public Socket getSocket() {
      return socket;
    }

    private void setSocket(Socket socket) {
      this.socket = socket;
    }
  }
}
