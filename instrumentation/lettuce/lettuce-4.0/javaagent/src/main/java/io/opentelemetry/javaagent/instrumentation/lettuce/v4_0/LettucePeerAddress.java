/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

public class LettucePeerAddress {
  private final boolean omitOnDifferentAddress;
  @Nullable private InetSocketAddress address;
  private boolean ambiguous;

  LettucePeerAddress() {
    this(false);
  }

  private LettucePeerAddress(boolean omitOnDifferentAddress) {
    this.omitOnDifferentAddress = omitOnDifferentAddress;
  }

  LettucePeerAddress(InetSocketAddress address) {
    this(false);
    this.address = address;
  }

  static LettucePeerAddress forBatch() {
    return new LettucePeerAddress(true);
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
