/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;

class JedisNetworkAttributesGetterTest {

  @Test
  void usesConnectedSocketAddress() {
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

    assertThat(new JedisDbAttributesGetter().getNetworkPeerInetSocketAddress(request, null))
        .isEqualTo(peerAddress);
  }

  @Test
  void dropsMissingSocketAddress() {
    JedisRequest request = JedisRequest.create(new Connection(), Protocol.Command.GET);

    assertThat(new JedisDbAttributesGetter().getNetworkPeerInetSocketAddress(request, null))
        .isNull();
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

    assertThat(new JedisDbAttributesGetter().getNetworkPeerInetSocketAddress(request, null))
        .isNull();
  }

  @Test
  void peerAddressIsSnapshot() {
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

    assertThat(new JedisDbAttributesGetter().getNetworkPeerInetSocketAddress(request, null))
        .isEqualTo(first);
  }
}
