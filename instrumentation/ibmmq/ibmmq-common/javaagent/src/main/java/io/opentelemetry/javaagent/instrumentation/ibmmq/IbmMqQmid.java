/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

// Dedicated VirtualField value type so the (Message, X) slot can never collide with another
// module's pair (VirtualField storage is keyed on the literal type pair, not the module).
public final class IbmMqQmid {

  private final String value;

  public IbmMqQmid(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
