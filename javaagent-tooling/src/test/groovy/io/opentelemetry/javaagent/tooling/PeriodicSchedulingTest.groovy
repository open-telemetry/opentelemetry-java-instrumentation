/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import static java.util.concurrent.TimeUnit.MILLISECONDS

import io.opentelemetry.instrumentation.test.utils.GcUtils
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import spock.lang.Specification

class PeriodicSchedulingTest extends Specification {

  def "test scheduling"() {
    setup:
    def latch = new CountDownLatch(2)
    def task = new CommonTaskExecutor.Task<CountDownLatch>() {
      @Override
      void run(CountDownLatch target) {
        target.countDown()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(task, latch, 10, 10, MILLISECONDS, "test")

    then:
    latch.await(500, MILLISECONDS)
  }

  def "test canceling"() {
    setup:
    def callCount = new AtomicInteger()
    def target = new WeakReference(new Object())
    def task = new CommonTaskExecutor.Task<Object>() {
      @Override
      void run(Object t) {
        callCount.countDown()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(task, target.get(), 10, 10, MILLISECONDS, "test")
    GcUtils.awaitGc(target)
    Thread.sleep(1)
    def snapshot = callCount.get()
    Thread.sleep(11)

    then:
    snapshot == callCount.get()
  }

  def "test null target"() {
    setup:
    def callCount = new AtomicInteger()
    def task = new CommonTaskExecutor.Task<Object>() {
      @Override
      void run(Object t) {
        callCount.countDown()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    def future = CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(task, null, 10, 10, MILLISECONDS, "test")
    Thread.sleep(11)

    then:
    future.isCancelled()
    callCount.get() == 0
  }
}
