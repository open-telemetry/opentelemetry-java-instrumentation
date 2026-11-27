/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7.internal;

import javax.annotation.Nullable;

/**
 * Lets the javaagent end a record processing invocation opened by early (before transaction)
 * interception when Spring Kafka versions without poll thread state callbacks (before 2.8) skip the
 * interceptor completion callbacks.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface EarlyInterceptionCleanup {

  /**
   * Returns the processing invocation that is active on the current thread, or {@code null} when
   * there is none or when poll thread state callbacks are responsible for ending it.
   */
  @Nullable
  Object getActiveInvocation();

  /**
   * Ends the given invocation with the given error if it is still the active invocation on the
   * current thread.
   */
  void endActiveInvocation(Object invocation, Throwable error);
}
