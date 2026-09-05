/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoNetworkPeer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

class MongoConnectionPeerTest {

  @Test
  void correlatesEachConnectedSocketWithItsConnectionDescription() throws IOException {
    ConnectionDescription firstDescription = connectionDescription(1);
    ConnectionDescription secondDescription = connectionDescription(2);

    InetSocketAddress firstPeer = captureConnectedPeer(firstDescription);
    InetSocketAddress secondPeer = captureConnectedPeer(secondDescription);

    assertThat(MongoConnectionPeer.resolve(firstDescription).getInetSocketAddress())
        .isEqualTo(firstPeer);
    assertThat(MongoConnectionPeer.resolve(secondDescription).getInetSocketAddress())
        .isEqualTo(secondPeer);
    assertThat(firstPeer).isNotEqualTo(secondPeer);
  }

  @Test
  void failedAndMissingSocketCapturesDoNotLeakToTheNextConnection() throws IOException {
    ConnectionDescription failedDescription = connectionDescription(1);
    MongoConnectionPeer.OpenState failedState = MongoConnectionPeer.startOpen();
    try (ConnectedSocket connectedSocket = ConnectedSocket.open()) {
      MongoConnectionPeer.capture(connectedSocket.client);
      MongoConnectionPeer.endOpen(
          failedState, failedDescription, new IOException("handshake failed"));
    }

    ConnectionDescription nextDescription = connectionDescription(2);
    MongoConnectionPeer.OpenState nextState = MongoConnectionPeer.startOpen();
    MongoConnectionPeer.endOpen(nextState, nextDescription, null);

    assertThat(MongoConnectionPeer.resolve(failedDescription)).isNull();
    assertThat(MongoConnectionPeer.resolve(nextDescription)).isNull();
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_16)
  void capturesUnixSocketWithoutAPort() throws ReflectiveOperationException {
    String socketPath = Paths.get("/tmp/mongodb-27017.sock").toString();
    SocketAddress unixSocketAddress =
        (SocketAddress)
            Class.forName("java.net.UnixDomainSocketAddress")
                .getMethod("of", String.class)
                .invoke(null, socketPath);
    Socket socket = socketConnectedTo(unixSocketAddress);
    ConnectionDescription connectionDescription = connectionDescription(1);
    MongoConnectionPeer.OpenState state = MongoConnectionPeer.startOpen();

    MongoConnectionPeer.capture(socket);
    MongoConnectionPeer.endOpen(state, connectionDescription, null);

    MongoNetworkPeer peer = MongoConnectionPeer.resolve(connectionDescription);
    assertThat(peer.getAddress()).isEqualTo(socketPath);
    assertThat(peer.getPort()).isNull();
  }

  private static InetSocketAddress captureConnectedPeer(ConnectionDescription connectionDescription)
      throws IOException {
    MongoConnectionPeer.OpenState state = MongoConnectionPeer.startOpen();
    try (ConnectedSocket connectedSocket = ConnectedSocket.open()) {
      InetSocketAddress peer = (InetSocketAddress) connectedSocket.client.getRemoteSocketAddress();
      MongoConnectionPeer.capture(connectedSocket.client);
      MongoConnectionPeer.endOpen(state, connectionDescription, null);
      return peer;
    }
  }

  private static ConnectionDescription connectionDescription(int port) {
    return new ConnectionDescription(
        new ServerId(new ClusterId(), new ServerAddress("configured.example", port)));
  }

  private static Socket socketConnectedTo(SocketAddress remoteAddress) {
    return new Socket() {
      @Override
      public boolean isConnected() {
        return true;
      }

      @Override
      public SocketAddress getRemoteSocketAddress() {
        return remoteAddress;
      }
    };
  }

  private static class ConnectedSocket implements AutoCloseable {
    private final ServerSocket server;
    private final Socket client;
    private final Socket accepted;

    private ConnectedSocket(ServerSocket server, Socket client, Socket accepted) {
      this.server = server;
      this.client = client;
      this.accepted = accepted;
    }

    private static ConnectedSocket open() throws IOException {
      ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
      Socket client = new Socket(server.getInetAddress(), server.getLocalPort());
      return new ConnectedSocket(server, client, server.accept());
    }

    @Override
    public void close() throws IOException {
      accepted.close();
      client.close();
      server.close();
    }
  }
}
