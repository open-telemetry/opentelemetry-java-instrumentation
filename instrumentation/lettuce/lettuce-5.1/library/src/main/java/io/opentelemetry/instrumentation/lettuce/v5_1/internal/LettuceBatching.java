/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1.internal;

import io.lettuce.core.protocol.RedisCommand;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Seam that lets the javaagent contribute Lettuce auto-flush batch aggregation without the library
 * module depending on agent-only APIs such as {@code VirtualField}. The agent registers a {@link
 * BatchHook} when its instrumentation loads; in library-only usage no hook is registered and
 * batching is inert.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LettuceBatching {

  @Nullable private static volatile BatchHook hook;

  public static void setHook(BatchHook batchHook) {
    hook = batchHook;
  }

  @Nullable
  static BatchScope captureCurrent(BatchSpan span) {
    BatchHook batchHook = hook;
    return batchHook == null ? null : batchHook.captureCurrent(span);
  }

  /**
   * Agent-provided entry point that links a command span to the current batch, if any.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public interface BatchHook {
    @Nullable
    BatchScope captureCurrent(BatchSpan span);
  }

  /**
   * Handle to an in-flight batch that a command span notifies as it completes.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public interface BatchScope {
    void finishOne(BatchSpan span);
  }

  /**
   * View of a command span used to build and complete the aggregate batch span.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public interface BatchSpan {
    BatchSpan createAggregateSpan(List<RedisCommand<?, ?, ?>> commands);

    @Nullable
    String getBatchErrorMessage();

    @Nullable
    Throwable getError();

    void finishWithResponse(@Nullable String errorMessage, @Nullable Throwable error);
  }

  private LettuceBatching() {}
}
