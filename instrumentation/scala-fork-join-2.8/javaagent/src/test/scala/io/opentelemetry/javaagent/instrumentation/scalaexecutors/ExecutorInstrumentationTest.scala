/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.javaagent.instrumentation.executors.AbstractExecutorServiceTest
import io.opentelemetry.javaagent.instrumentation.scalaexecutors.ExecutorInstrumentationTest.testing
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.function.ThrowingConsumer

import scala.concurrent.forkjoin.{ForkJoinPool, ForkJoinTask}

class ExecutorInstrumentationTest
    extends AbstractExecutorServiceTest[ForkJoinPoolBridge, AsyncChild](
      new ForkJoinPoolBridge(new ForkJoinPool),
      testing
    ) {

  @RegisterExtension val extension = testing

  override protected def newTask(
      doTraceableWork: Boolean,
      blockThread: Boolean
  ): AsyncChild = {
    new AsyncChild(doTraceableWork, blockThread)
  }

  @Test
  def invokeForkJoinTask(): Unit = {
    executeTwoTasks(new ThrowingConsumer[AsyncChild]() {
      override def accept(task: AsyncChild): Unit =
        executor().invoke(task.asInstanceOf[ForkJoinTask[_]])
    })
  }

  @Test
  def executeForkJoinTask(): Unit = {
    executeTwoTasks(new ThrowingConsumer[AsyncChild]() {
      override def accept(task: AsyncChild): Unit =
        executor().execute(task.asInstanceOf[ForkJoinTask[_]])
    })
  }
}

object ExecutorInstrumentationTest {
  val testing: AgentInstrumentationExtension =
    AgentInstrumentationExtension.create
}
