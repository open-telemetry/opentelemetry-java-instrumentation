/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

/**
 * Helper class to access package-private members of {@link NettyRequest} from the internal
 * package.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class NettyRequestAccessor {

  /**
   * Returns the underlying common NettyRequest from a v4.1 NettyRequest. This method is for
   * internal use only.
   */
  public static io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest getDelegate(
      NettyRequest request) {
    return request.delegate();
  }

  private NettyRequestAccessor() {}
}
