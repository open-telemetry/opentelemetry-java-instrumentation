/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.executors.AbstractExecutorServiceTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import scala.concurrent.forkjoin.ForkJoinPool;
import scala.concurrent.forkjoin.ForkJoinTask;

class ScalaExecutorInstrumentationTest
    extends AbstractExecutorServiceTest<ForkJoinPool, ScalaAsyncChild> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  ScalaExecutorInstrumentationTest() {
    super(new ForkJoinPool(), testing);
  }

  @Override
  protected ScalaAsyncChild newTask(boolean doTraceableWork, boolean blockThread) {
    return new ScalaAsyncChild(doTraceableWork, blockThread);
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
