/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.thrift;

import java.util.Map;

// Helper for accessing non-public members.
public final class AsyncProcessorUtil {

  @SuppressWarnings("unchecked") // casting generic map to a more specific generic map
  public static Map<String, AsyncProcessFunction<?, ?, ?, ?>> getProcessMap(
      TBaseAsyncProcessor<?> processor) {
    Map<String, ? extends AsyncProcessFunction<?, ?, ?, ?>> map = processor.processMap;
    return (Map<String, AsyncProcessFunction<?, ?, ?, ?>>) map;
  }

  public static boolean isOneWay(AsyncProcessFunction<?, ?, ?, ?> asyncProcessFunction) {
    // protected in 0.13.0, public in later versions
    return asyncProcessFunction.isOneway();
  }

  private AsyncProcessorUtil() {}
}
