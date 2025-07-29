/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors

import java.util.concurrent._
import scala.concurrent.forkjoin.{ForkJoinPool, ForkJoinTask}

class ForkJoinPoolBridge(delegate: ForkJoinPool) extends ExecutorService {

  override def shutdown(): Unit = delegate.shutdown()

  override def shutdownNow(): java.util.List[Runnable] = delegate.shutdownNow()

  override def isShutdown: Boolean = delegate.isShutdown

  override def isTerminated: Boolean = delegate.isTerminated

  override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
    delegate.awaitTermination(timeout, unit)

  override def submit[T](task: Callable[T]): Future[T] = delegate.submit(task)

  override def submit[T](task: Runnable, result: T): Future[T] =
    delegate.submit(task, result)

  override def submit(task: Runnable): Future[_] = delegate.submit(task)

  override def invokeAll[T](
      tasks: java.util.Collection[_ <: Callable[T]]
  ): java.util.List[Future[T]] =
    delegate.invokeAll(tasks)

  override def invokeAll[T](
      tasks: java.util.Collection[_ <: Callable[T]],
      timeout: Long,
      unit: TimeUnit
  ): java.util.List[Future[T]] =
    delegate.invokeAll(tasks)

  override def invokeAny[T](tasks: java.util.Collection[_ <: Callable[T]]): T =
    delegate.submit(tasks.iterator().next()).get()

  override def invokeAny[T](
      tasks: java.util.Collection[_ <: Callable[T]],
      timeout: Long,
      unit: TimeUnit
  ): T =
    delegate.submit(tasks.iterator().next()).get()

  def invoke[T](task: ForkJoinTask[T]): T = delegate.invoke(task)

  override def execute(command: Runnable): Unit = delegate.execute(command)

  def execute[T](task: ForkJoinTask[T]): Unit = delegate.execute(task)
}
