/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class LettuceCommandPeer {
  @Nullable private InetSocketAddress address;
  private boolean ambiguous;

  synchronized void record(InetSocketAddress address) {
    if (ambiguous) {
      return;
    }
    if (this.address == null) {
      this.address = address;
    } else if (!this.address.equals(address)) {
      this.address = null;
      ambiguous = true;
    }
  }

  @Nullable
  synchronized InetSocketAddress getAddress() {
    return address;
  }
}
