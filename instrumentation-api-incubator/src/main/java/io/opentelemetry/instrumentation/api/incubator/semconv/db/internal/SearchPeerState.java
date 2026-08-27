/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SearchPeerState {

  private static final ContextKey<StackEntry> KEY =
      ContextKey.named("opentelemetry-search-peer-state");

  @Nullable private volatile InetSocketAddress peerAddress;

  public static boolean isActive(Context context) {
    return context.get(KEY) != null;
  }

  public static void capture(Context context, @Nullable SocketAddress peerAddress) {
    if (!(peerAddress instanceof InetSocketAddress)) {
      return;
    }
    InetSocketAddress inetPeerAddress = (InetSocketAddress) peerAddress;
    if (inetPeerAddress.isUnresolved() || inetPeerAddress.getAddress() == null) {
      return;
    }

    StackEntry entry = context.get(KEY);
    while (entry != null) {
      entry.state.peerAddress = inetPeerAddress;
      entry = entry.previous;
    }
  }

  public Context storeInContext(Context context) {
    return context.with(KEY, new StackEntry(this, context.get(KEY)));
  }

  @Nullable
  public InetSocketAddress getPeerAddress() {
    return peerAddress;
  }

  private static final class StackEntry {
    private final SearchPeerState state;
    @Nullable private final StackEntry previous;

    private StackEntry(SearchPeerState state, @Nullable StackEntry previous) {
      this.state = state;
      this.previous = previous;
    }
  }
}
