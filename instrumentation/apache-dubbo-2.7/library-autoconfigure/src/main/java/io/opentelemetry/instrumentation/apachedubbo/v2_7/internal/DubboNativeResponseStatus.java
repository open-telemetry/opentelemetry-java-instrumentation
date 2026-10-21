/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboNativeResponseStatus {

  private static volatile Accessor accessor;

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public interface Accessor {

    @Nullable
    Object enterClientInvocation(Object invocation);

    void exitClientInvocation(Object scope);

    void associateClientRequest(Object request);

    void associateServerInvocation(Object invocation);

    @Nullable
    String getStatusCode(Object invocation);

    void clear(Object invocation);

    boolean deferServerCompletion(Object invocation, Runnable completion);
  }

  public static void setAccessor(Accessor accessor) {
    DubboNativeResponseStatus.accessor = accessor;
  }

  @Nullable
  public static Object enterClientInvocation(Object invocation) {
    Accessor currentAccessor = accessor;
    return currentAccessor == null ? null : currentAccessor.enterClientInvocation(invocation);
  }

  public static void exitClientInvocation(Object scope) {
    Accessor currentAccessor = accessor;
    if (currentAccessor != null) {
      currentAccessor.exitClientInvocation(scope);
    }
  }

  public static void associateClientRequest(Object request) {
    Accessor currentAccessor = accessor;
    if (currentAccessor != null) {
      currentAccessor.associateClientRequest(request);
    }
  }

  public static void associateServerInvocation(Object invocation) {
    Accessor currentAccessor = accessor;
    if (currentAccessor != null) {
      currentAccessor.associateServerInvocation(invocation);
    }
  }

  @Nullable
  public static String getStatusCode(Object invocation) {
    Accessor currentAccessor = accessor;
    return currentAccessor == null ? null : currentAccessor.getStatusCode(invocation);
  }

  public static void clear(Object invocation) {
    Accessor currentAccessor = accessor;
    if (currentAccessor != null) {
      currentAccessor.clear(invocation);
    }
  }

  public static boolean deferServerCompletion(Object invocation, Runnable completion) {
    Accessor currentAccessor = accessor;
    return currentAccessor != null && currentAccessor.deferServerCompletion(invocation, completion);
  }

  private DubboNativeResponseStatus() {}
}
