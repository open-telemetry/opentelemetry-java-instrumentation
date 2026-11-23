/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import java.net.SocketAddress;
import javax.annotation.Nullable;

final class LettuceCommandPeer {
  // Completion and outbound writes can race on different threads.
  @Nullable private SocketAddress address;
  private boolean finished;

  synchronized void record(SocketAddress address) {
    if (!finished) {
      this.address = address;
    }
  }

  synchronized void finish() {
    finished = true;
  }

  @Nullable
  synchronized SocketAddress getAddress() {
    return address;
  }
}
