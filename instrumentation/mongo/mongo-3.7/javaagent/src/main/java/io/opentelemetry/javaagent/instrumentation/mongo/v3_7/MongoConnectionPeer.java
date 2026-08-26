/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import com.mongodb.connection.ConnectionDescription;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public final class MongoConnectionPeer {

  private static final VirtualField<ConnectionDescription, InetSocketAddress> CONNECTION_PEER =
      VirtualField.find(ConnectionDescription.class, InetSocketAddress.class);

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

    SocketAddress remoteAddress = socket.getRemoteSocketAddress();
    if (!(remoteAddress instanceof InetSocketAddress)) {
      return;
    }

    InetSocketAddress peer = (InetSocketAddress) remoteAddress;
    if (!peer.isUnresolved() && peer.getAddress() != null) {
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
  public static InetSocketAddress resolve(ConnectionDescription connectionDescription) {
    return CONNECTION_PEER.get(connectionDescription);
  }

  public static final class OpenState {
    @Nullable private final OpenState previous;
    @Nullable private InetSocketAddress peer;

    private OpenState(@Nullable OpenState previous) {
      this.previous = previous;
    }
  }

  private MongoConnectionPeer() {}
}
