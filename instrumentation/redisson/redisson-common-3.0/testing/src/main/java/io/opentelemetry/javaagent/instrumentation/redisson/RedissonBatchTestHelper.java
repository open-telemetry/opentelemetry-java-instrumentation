/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import org.redisson.api.BatchOptions;

final class RedissonBatchTestHelper {

  static BatchOptions batchOptions(String executionMode) {
    BatchOptions options = BatchOptions.defaults();
    setExecutionMode(options, executionMode);
    return options;
  }

  static void setExecutionMode(BatchOptions options, String executionMode) {
    try {
      Class<?> executionModeClass = Class.forName("org.redisson.api.BatchOptions$ExecutionMode");
      Object mode =
          executionModeClass.getMethod("valueOf", String.class).invoke(null, executionMode);
      BatchOptions.class.getMethod("executionMode", executionModeClass).invoke(options, mode);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private RedissonBatchTestHelper() {}
}
