/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.scalaexecutors

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.javaagent.instrumentation.executors.TestTask

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.forkjoin.ForkJoinTask

final class AsyncChild(doTraceableWork: Boolean, blockThread: Boolean)
    extends ForkJoinTask[AnyRef]
    with TestTask {
  private val tracer: Tracer = GlobalOpenTelemetry.getTracer("test")
  private val blockThreadAtomic: AtomicBoolean = new AtomicBoolean(blockThread)
  private val latch: CountDownLatch = new CountDownLatch(1)

  override def getRawResult: AnyRef = null

  override def setRawResult(value: AnyRef): Unit = {}

  override def exec(): Boolean = {
    runImpl()
    true
  }

  override def unblock(): Unit = {
    blockThreadAtomic.set(false)
  }

  override def run(): Unit = {
    runImpl()
  }

  override def call(): AnyRef = {
    runImpl()
    null
  }

  override def waitForCompletion(): Unit = {
    try {
      latch.await()
    } catch {
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        throw new AssertionError(e)
    }
  }

  private def runImpl(): Unit = {
    while (blockThreadAtomic.get()) {
      // busy-wait to block thread
    }
    if (doTraceableWork) {
      asyncChild()
    }
    latch.countDown()
  }

  private def asyncChild(): Unit = {
    tracer.spanBuilder("asyncChild").startSpan().end()
  }
}
