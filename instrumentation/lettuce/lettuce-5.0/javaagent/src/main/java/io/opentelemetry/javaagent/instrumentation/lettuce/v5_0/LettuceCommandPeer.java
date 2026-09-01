/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class LettuceCommandPeer {
  private final boolean omitOnDifferentAddress;
  @Nullable private InetSocketAddress address;
  private boolean ambiguous;

  LettuceCommandPeer() {
    this(false);
  }

  private LettuceCommandPeer(boolean omitOnDifferentAddress) {
    this.omitOnDifferentAddress = omitOnDifferentAddress;
  }

  static LettuceCommandPeer forBatch() {
    return new LettuceCommandPeer(true);
  }

  synchronized void record(InetSocketAddress address) {
    if (ambiguous) {
      return;
    }
    if (omitOnDifferentAddress && this.address != null && !this.address.equals(address)) {
      this.address = null;
      ambiguous = true;
      return;
    }
    this.address = address;
  }

  @Nullable
  synchronized InetSocketAddress getAddress() {
    return address;
  }
}
