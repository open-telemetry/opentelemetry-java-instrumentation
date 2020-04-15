/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling

import io.opentelemetry.auto.common.exec.CommonTaskExecutor
import io.opentelemetry.auto.util.gc.GCUtils
import io.opentelemetry.auto.util.test.AgentSpecification
import spock.lang.Retry
import spock.lang.Subject

import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import static java.util.concurrent.TimeUnit.MILLISECONDS

@Retry
class CleanerTest extends AgentSpecification {

  @Subject
  def cleaner = new Cleaner()

  def "test scheduling"() {
    setup:
    def latch = new CountDownLatch(2)
    def target = new Object()
    def action = new Cleaner.Adapter<Object>() {
      @Override
      void clean(Object t) {
        latch.countDown()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    cleaner.scheduleCleaning(target, action, 10, MILLISECONDS)

    then:
    latch.await(500, MILLISECONDS)
  }

  def "test canceling"() {
    setup:
    def callCount = new AtomicInteger()
    def target = new WeakReference(new Object())
    def action = new Cleaner.Adapter<Object>() {
      @Override
      void clean(Object t) {
        callCount.incrementAndGet()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    cleaner.scheduleCleaning(target.get(), action, 10, MILLISECONDS)
    GCUtils.awaitGC(target)
    Thread.sleep(1)
    def snapshot = callCount.get()
    Thread.sleep(11)

    then:
    snapshot == callCount.get()
  }

  def "test null target"() {
    setup:
    def callCount = new AtomicInteger()
    def action = new Cleaner.Adapter<Object>() {
      @Override
      void clean(Object t) {
        callCount.incrementAndGet()
      }
    }

    expect:
    !CommonTaskExecutor.INSTANCE.isShutdown()

    when:
    cleaner.scheduleCleaning(null, action, 10, MILLISECONDS)
    Thread.sleep(11)

    then:
    callCount.get() == 0
  }
}
