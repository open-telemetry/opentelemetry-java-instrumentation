/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.xxljob;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;

/**
 * Helper class for managing ContextQueue instances associated with JobThread.
 *
 * <p>This class provides static methods to manipulate the ContextQueue stored in a {@link
 * VirtualField}. The purpose of this helper is to avoid bytecode constant pool issues that can
 * occur when directly instantiating {@link ContextQueue} in ByteBuddy Advice methods.
 */
public final class ContextQueueHelper {

  private ContextQueueHelper() {}

  public static <T> void offerContext(
      T jobThread, VirtualField<T, ContextQueue> virtualField, Context context) {
    if (jobThread == null || context == null) {
      return;
    }

    ContextQueue contextQueue = virtualField.get(jobThread);
    if (contextQueue == null) {
      // Create ContextQueue in this helper method to avoid direct instantiation in Advice
      contextQueue = new ContextQueue();
      virtualField.set(jobThread, contextQueue);
    }

    contextQueue.offer(context);
  }

  public static <T> Context pollContext(VirtualField<T, ContextQueue> virtualField, T jobThread) {
    if (jobThread == null) {
      return null;
    }

    ContextQueue contextQueue = virtualField.get(jobThread);
    if (contextQueue != null) {
      return contextQueue.poll();
    }

    return null;
  }

  public static <T> void clearContextQueue(
      VirtualField<T, ContextQueue> virtualField, T jobThread) {
    if (jobThread == null) {
      return;
    }

    ContextQueue contextQueue = virtualField.get(jobThread);
    if (contextQueue != null) {
      contextQueue.clear();
      virtualField.set(jobThread, null);
    }
  }
}
