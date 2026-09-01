/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
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
  void ordinaryCommandDoesNotBecomeTransactionFramingRequest() {
    JedisRequest request =
        requestWithPeer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6379));
    request.capturePeerAddress();
    Transaction transaction = new Transaction();

    JedisPipelineContext.captureTransactionFramingPeer(request);
    JedisPipelineContext.exitTransactionFraming(transaction);

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

    JedisPipelineContext.enterTransactionFraming(transactionRequest);
    try {
      execRequest.capturePeerAddress();
      JedisPipelineContext.captureTransactionFramingPeer(execRequest);
    } finally {
      JedisPipelineContext.exitTransactionFraming();
    }

    assertThat(transactionRequest.getPeerAddress()).isEqualTo(execPeer);
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

    JedisPipelineContext.enterTransactionFraming(transactionRequest);
    try {
      JedisPipelineContext.captureTransactionFramingPeer(execRequest);
    } finally {
      JedisPipelineContext.exitTransactionFraming();
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

  private static Stream<InetAddress> resolvedAddresses() throws UnknownHostException {
    return Stream.of(
        InetAddress.getByAddress(new byte[] {127, 0, 0, 1}),
        InetAddress.getByAddress(new byte[16]));
  }
}
