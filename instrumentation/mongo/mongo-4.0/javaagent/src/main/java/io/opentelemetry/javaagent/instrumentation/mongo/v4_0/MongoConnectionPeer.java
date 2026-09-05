/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import com.mongodb.connection.ConnectionDescription;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoNetworkPeer;
import java.net.Socket;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class MongoConnectionPeer {

  private static final VirtualField<ConnectionDescription, MongoNetworkPeer> CONNECTION_PEER =
      VirtualField.find(ConnectionDescription.class, MongoNetworkPeer.class);

  private static final ThreadLocal<OpenState> currentOpen = new ThreadLocal<>();

  public static OpenState startOpen() {
    OpenState state = new OpenState(currentOpen.get());
    currentOpen.set(state);
    return state;
  }

  public static void capture(Socket socket) {
    OpenState state = currentOpen.get();
    if (state == null || !socket.isConnected()) {
      return;
    }
    capture(state, socket.getRemoteSocketAddress());
  }

  public static void capture(@Nullable SocketAddress remoteAddress) {
    OpenState state = currentOpen.get();
    if (state == null) {
      return;
    }
    capture(state, remoteAddress);
  }

  private static void capture(OpenState state, @Nullable SocketAddress remoteAddress) {
    MongoNetworkPeer peer = MongoNetworkPeer.fromSocketAddress(remoteAddress);
    if (peer != null) {
      state.peer = peer;
    }
  }

  public static void endOpen(
      OpenState state,
      @Nullable ConnectionDescription connectionDescription,
      @Nullable Throwable error) {
    if (currentOpen.get() == state) {
      if (state.previous == null) {
        currentOpen.remove();
      } else {
        currentOpen.set(state.previous);
      }
    }

    if (error == null && connectionDescription != null && state.peer != null) {
      CONNECTION_PEER.set(connectionDescription, state.peer);
    }
  }

  @Nullable
  public static MongoNetworkPeer resolve(ConnectionDescription connectionDescription) {
    return CONNECTION_PEER.get(connectionDescription);
  }

  public static class OpenState {
    @Nullable private final OpenState previous;
    @Nullable private MongoNetworkPeer peer;

    private OpenState(@Nullable OpenState previous) {
      this.previous = previous;
    }
  }

  private MongoConnectionPeer() {}
}
