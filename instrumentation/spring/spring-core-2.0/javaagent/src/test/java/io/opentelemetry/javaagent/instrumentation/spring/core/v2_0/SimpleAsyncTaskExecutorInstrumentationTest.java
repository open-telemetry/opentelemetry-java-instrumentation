/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.core.v2_0;

import static io.opentelemetry.javaagent.instrumentation.spring.core.v2_0.AsyncTaskTestUtil.executeTwoTasks;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

class SimpleAsyncTaskExecutorInstrumentationTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();

  @Test
  void executeRunnable() {
    executeTwoTasks(testing, executor::execute);
  }
}
