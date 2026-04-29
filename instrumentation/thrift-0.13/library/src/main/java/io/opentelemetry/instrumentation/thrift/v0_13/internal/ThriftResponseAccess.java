/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.instrumentation.api.internal.Initializer;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThriftResponseAccess {
  private static Access access;

  static {
    // initialize ThriftResponse class, so that Access can be set
    try {
      Class.forName(ThriftResponse.class.getName());
    } catch (ClassNotFoundException e) {
      // ignore
    }
  }

  static ThriftResponse failed() {
    return access.failed();
  }

  @Initializer
  public static void setAccess(Access access) {
    ThriftResponseAccess.access = access;
  }

  private ThriftResponseAccess() {}

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public interface Access {

    ThriftResponse failed();
  }
}
