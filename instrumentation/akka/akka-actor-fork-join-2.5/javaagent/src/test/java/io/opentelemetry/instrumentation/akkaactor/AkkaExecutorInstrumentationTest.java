/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.akkaactor;

import akka.dispatch.forkjoin.ForkJoinPool;
import akka.dispatch.forkjoin.ForkJoinTask;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.executors.AbstractExecutorServiceTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AkkaExecutorInstrumentationTest
    extends AbstractExecutorServiceTest<ForkJoinPool, AkkaAsyncChild> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  AkkaExecutorInstrumentationTest() {
    super(new ForkJoinPool(), testing);
  }

  @Override
  protected AkkaAsyncChild newTask(boolean doTraceableWork, boolean blockThread) {
    return new AkkaAsyncChild(doTraceableWork, blockThread);
  }

  @Test
  void invokeForkJoinTask() {
    executeTwoTasks(task -> executor().invoke((ForkJoinTask<?>) task));
  }

  @Test
  void submitForkJoinTask() {
    executeTwoTasks(task -> executor().submit((ForkJoinTask<?>) task));
  }
}
