/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.extension.RegisterExtension;

class VirtualThreadExecutorTest
    extends AbstractExecutorServiceTest<ExecutorService, JavaAsyncChild> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  VirtualThreadExecutorTest() {
    super(Executors.newVirtualThreadPerTaskExecutor(), testing);
  }

  @Override
  protected JavaAsyncChild newTask(boolean doTraceableWork, boolean blockThread) {
    return new JavaAsyncChild(doTraceableWork, blockThread);
  }
}
