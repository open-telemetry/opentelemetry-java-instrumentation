/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.common.util.concurrent.MoreExecutors
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.javaagent.instrumentation.spymemcached.CompletionListener
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import net.spy.memcached.CASResponse
import net.spy.memcached.ConnectionFactory
import net.spy.memcached.ConnectionFactoryBuilder
import net.spy.memcached.DefaultConnectionFactory
import net.spy.memcached.MemcachedClient
import net.spy.memcached.internal.CheckedOperationTimeoutException
import net.spy.memcached.ops.Operation
import net.spy.memcached.ops.OperationQueueFactory
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.ReentrantLock

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static net.spy.memcached.ConnectionFactoryBuilder.Protocol.BINARY

class SpymemcachedTest extends AgentInstrumentationSpecification {

  @Shared
  def parentOperation = "parent-span"
  @Shared
  def expiration = 3600
  @Shared
  def keyPrefix = "SpymemcachedTest-" + (Math.abs(new Random().nextInt())) + "-"
  @Shared
  def timingOutMemcachedOpTimeout = 1000

  @Shared
  def memcachedContainer
  @Shared
  InetSocketAddress memcachedAddress

  def setupSpec() {
    memcachedContainer = new GenericContainer('memcached:latest')
      .withExposedPorts(11211)
      .withStartupTimeout(Duration.ofSeconds(120))
    memcachedContainer.start()
    memcachedAddress = new InetSocketAddress(
      memcachedContainer.containerIpAddress,
      memcachedContainer.getMappedPort(11211)
    )
  }

  def cleanupSpec() {
    if (memcachedContainer) {
      memcachedContainer.stop()
    }
  }

  ReentrantLock queueLock
  MemcachedClient memcached
  MemcachedClient lockableMemcached
  MemcachedClient timingoutMemcached

  def setup() {
    queueLock = new ReentrantLock()

    // Use direct executor service so our listeners finish in deterministic order
    ExecutorService listenerExecutorService = MoreExecutors.newDirectExecutorService()

    ConnectionFactory connectionFactory = (new ConnectionFactoryBuilder())
      .setListenerExecutorService(listenerExecutorService)
      .setProtocol(BINARY)
      .build()
    memcached = new MemcachedClient(connectionFactory, Arrays.asList(memcachedAddress))

    def lockableQueueFactory = new OperationQueueFactory() {
      @Override
      BlockingQueue<Operation> create() {
        return getLockableQueue(queueLock)
      }
    }

    ConnectionFactory lockableConnectionFactory = (new ConnectionFactoryBuilder())
      .setListenerExecutorService(listenerExecutorService)
      .setProtocol(BINARY)
      .setOpQueueFactory(lockableQueueFactory)
      .build()
    lockableMemcached = new MemcachedClient(lockableConnectionFactory, Arrays.asList(memcachedAddress))

    ConnectionFactory timingoutConnectionFactory = (new ConnectionFactoryBuilder())
      .setListenerExecutorService(listenerExecutorService)
      .setProtocol(BINARY)
      .setOpQueueFactory(lockableQueueFactory)
      .setOpTimeout(timingOutMemcachedOpTimeout)
      .build()
    timingoutMemcached = new MemcachedClient(timingoutConnectionFactory, Arrays.asList(memcachedAddress))

    // Add some keys to test on later:
    def valuesToSet = [
      "test-get"    : "get test",
      "test-get-2"  : "get test 2",
      "test-append" : "append test",
      "test-prepend": "prepend test",
      "test-delete" : "delete test",
      "test-replace": "replace test",
      "test-touch"  : "touch test",
      "test-cas"    : "cas test",
      "test-decr"   : "200",
      "test-incr"   : "100"
    ]
    runWithSpan("setup") {
      valuesToSet.each { k, v -> assert memcached.set(key(k), expiration, v).get() }
    }
    ignoreTracesAndClear(1)
  }

  def "test get hit"() {
    when:
    runWithSpan(parentOperation) {
      assert "get test" == memcached.get(key("test-get"))
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "get", null, "hit")
      }
    }
  }

  def "test get miss"() {
    when:
    runWithSpan(parentOperation) {
      assert null == memcached.get(key("test-get-key-that-doesn't-exist"))
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "get", null, "miss")
      }
    }
  }

  def "test get cancel"() {
    when:
    runWithSpan(parentOperation) {
      queueLock.lock()
      lockableMemcached.asyncGet(key("test-get")).cancel(true)
      queueLock.unlock()
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "get", "canceled")
      }
    }
  }

  def "test get timeout"() {
    when:
    /*
     Not using runWithSpan since timeouts happen in separate thread
     and direct executor doesn't help to make sure that parent span finishes last.
     Instead run without parent span to have only 1 span to test with.
      */
    try {
      queueLock.lock()
      timingoutMemcached.asyncGet(key("test-get"))
      Thread.sleep(timingOutMemcachedOpTimeout + 1000)
    } finally {
      queueLock.unlock()
    }

    then:
    assertTraces(1) {
      trace(0, 1) {
        getSpan(it, 0, "get", "timeout")
      }
    }
  }

  def "test bulk get"() {
    when:
    runWithSpan(parentOperation) {
      def expected = [(key("test-get")): "get test", (key("test-get-2")): "get test 2"]
      assert expected == memcached.getBulk(key("test-get"), key("test-get-2"))
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "getBulk", null, null)
      }
    }
  }

  def "test set"() {
    when:
    runWithSpan(parentOperation) {
      assert memcached.set(key("test-set"), expiration, "bar").get()
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "set")
      }
    }
  }

  def "test set cancel"() {
    when:
    runWithSpan(parentOperation) {
      queueLock.lock()
      assert lockableMemcached.set(key("test-set-cancel"), expiration, "bar").cancel()
      queueLock.unlock()
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "set", "canceled")
      }
    }
  }

  def "test add"() {
    when:
    runWithSpan(parentOperation) {
      assert memcached.add(key("test-add"), expiration, "add bar").get()
      assert "add bar" == memcached.get(key("test-add"))
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        getParentSpan(it, 0)
        getSpan(it, 1, "add")
        getSpan(it, 2, "get", null, "hit")
      }
    }
  }

  def "test second add"() {
    when:
    runWithSpan(parentOperation) {
      assert memcached.add(key("test-add-2"), expiration, "add bar").get()
      assert !memcached.add(key("test-add-2"), expiration, "add bar 123").get()
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        getParentSpan(it, 0)
        getSpan(it, 1, "add")
        getSpan(it, 2, "add")
      }
    }
  }

  def "test delete"() {
    when:
    runWithSpan(parentOperation) {
      assert memcached.delete(key("test-delete")).get()
      assert null == memcached.get(key("test-delete"))
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        getParentSpan(it, 0)
        getSpan(it, 1, "delete")
        getSpan(it, 2, "get", null, "miss")
      }
    }
  }

  def "test delete non existent"() {
    when:
    runWithSpan(parentOperation) {
      assert !memcached.delete(key("test-delete-non-existent")).get()
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "delete")
      }
    }
  }

  def "test replace"() {
    when:
    runWithSpan(parentOperation) {
      assert memcached.replace(key("test-replace"), expiration, "new value").get()
      assert "new value" == memcached.get(key("test-replace"))
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        getParentSpan(it, 0)
        getSpan(it, 1, "replace")
        getSpan(it, 2, "get", null, "hit")
      }
    }
  }

  def "test replace non existent"() {
    when:
    runWithSpan(parentOperation) {
      assert !memcached.replace(key("test-replace-non-existent"), expiration, "new value").get()
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "replace")
      }
    }
  }

  def "test append"() {
    when:
    runWithSpan(parentOperation) {
      def cas = memcached.gets(key("test-append"))
      assert memcached.append(cas.cas, key("test-append"), " appended").get()
      assert "append test appended" == memcached.get(key("test-append"))
    }

    then:
    assertTraces(1) {
      trace(0, 4) {
        getParentSpan(it, 0)
        getSpan(it, 1, "gets")
        getSpan(it, 2, "append")
        getSpan(it, 3, "get", null, "hit")
      }
    }
  }

  def "test prepend"() {
    when:
    runWithSpan(parentOperation) {
      def cas = memcached.gets(key("test-prepend"))
      assert memcached.prepend(cas.cas, key("test-prepend"), "prepended ").get()
      assert "prepended prepend test" == memcached.get(key("test-prepend"))
    }

    then:
    assertTraces(1) {
      trace(0, 4) {
        getParentSpan(it, 0)
        getSpan(it, 1, "gets")
        getSpan(it, 2, "prepend")
        getSpan(it, 3, "get", null, "hit")
      }
    }
  }

  def "test cas"() {
    when:
    runWithSpan(parentOperation) {
      def cas = memcached.gets(key("test-cas"))
      assert CASResponse.OK == memcached.cas(key("test-cas"), cas.cas, expiration, "cas bar")
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        getParentSpan(it, 0)
        getSpan(it, 1, "gets")
        getSpan(it, 2, "cas")
      }
    }
  }

  def "test cas not found"() {
    when:
    runWithSpan(parentOperation) {
      assert CASResponse.NOT_FOUND == memcached.cas(key("test-cas-doesnt-exist"), 1234, expiration, "cas bar")
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "cas")
      }
    }
  }

  def "test touch"() {
    when:
    runWithSpan(parentOperation) {
      assert memcached.touch(key("test-touch"), expiration).get()
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "touch")
      }
    }
  }

  def "test touch non existent"() {
    when:
    runWithSpan(parentOperation) {
      assert !memcached.touch(key("test-touch-non-existent"), expiration).get()
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "touch")
      }
    }
  }

  def "test get and touch"() {
    when:
    runWithSpan(parentOperation) {
      assert "touch test" == memcached.getAndTouch(key("test-touch"), expiration).value
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "getAndTouch")
      }
    }
  }

  def "test get and touch non existent"() {
    when:
    runWithSpan(parentOperation) {
      assert null == memcached.getAndTouch(key("test-touch-non-existent"), expiration)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "getAndTouch")
      }
    }
  }

  def "test decr"() {
    when:
    runWithSpan(parentOperation) {
      /*
        Memcached is funny in the way it handles incr/decr operations:
        it needs values to be strings (with digits in them) and it returns actual long from decr/incr
       */
      assert 195 == memcached.decr(key("test-decr"), 5)
      assert "195" == memcached.get(key("test-decr"))
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        getParentSpan(it, 0)
        getSpan(it, 1, "decr")
        getSpan(it, 2, "get", null, "hit")
      }
    }
  }

  def "test decr non existent"() {
    when:
    runWithSpan(parentOperation) {
      assert -1 == memcached.decr(key("test-decr-non-existent"), 5)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "decr")
      }
    }
  }

  def "test decr exception"() {
    when:
    memcached.decr(key("long key: " + longString()), 5)

    then:
    thrown IllegalArgumentException
    assertTraces(1) {
      trace(0, 1) {
        getSpan(it, 0, "decr", "long key")
      }
    }
  }

  def "test incr"() {
    when:
    runWithSpan(parentOperation) {
      /*
        Memcached is funny in the way it handles incr/decr operations:
        it needs values to be strings (with digits in them) and it returns actual long from decr/incr
       */
      assert 105 == memcached.incr(key("test-incr"), 5)
      assert "105" == memcached.get(key("test-incr"))
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        getParentSpan(it, 0)
        getSpan(it, 1, "incr")
        getSpan(it, 2, "get", null, "hit")
      }
    }
  }

  def "test incr non existent"() {
    when:
    runWithSpan(parentOperation) {
      assert -1 == memcached.incr(key("test-incr-non-existent"), 5)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        getParentSpan(it, 0)
        getSpan(it, 1, "incr")
      }
    }
  }

  def "test incr exception"() {
    when:
    memcached.incr(key("long key: " + longString()), 5)

    then:
    thrown IllegalArgumentException
    assertTraces(1) {
      trace(0, 1) {
        getSpan(it, 0, "incr", "long key")
      }
    }
  }

  def key(String k) {
    keyPrefix + k
  }

  def longString(char c = 's' as char) {
    char[] chars = new char[250]
    Arrays.fill(chars, 's' as char)
    return new String(chars)
  }

  def getLockableQueue(ReentrantLock queueLock) {
    return new ArrayBlockingQueue<Operation>(DefaultConnectionFactory.DEFAULT_OP_QUEUE_LEN) {

      @Override
      int drainTo(Collection<? super Operation> c, int maxElements) {
        try {
          queueLock.lock()
          return super.drainTo(c, maxElements)
        } finally {
          queueLock.unlock()
        }
      }
    }
  }

  def getParentSpan(TraceAssert trace, int index) {
    return trace.span(index) {
      name parentOperation
      hasNoParent()
      attributes {
      }
    }
  }

  def getSpan(TraceAssert trace, int index, String operation, String error = null, String result = null) {
    return trace.span(index) {
      if (index > 0) {
        childOf(trace.span(0))
      }

      name operation
      kind CLIENT
      if (error != null && error != "canceled") {
        status ERROR
      }

      if (error == "timeout") {
        errorEvent(
          CheckedOperationTimeoutException,
          "Operation timed out. - failing node: ${memcachedAddress.address}:${memcachedAddress.port}")
      }

      if (error == "long key") {
        errorEvent(
          IllegalArgumentException,
          "Key is too long (maxlen = 250)")
      }

      attributes {
        "${SemanticAttributes.DB_SYSTEM.key}" "memcached"
        "${SemanticAttributes.DB_OPERATION.key}" operation

        if (error == "canceled") {
          "${CompletionListener.DB_COMMAND_CANCELLED}" true
        }

        if (result == "hit") {
          "${CompletionListener.MEMCACHED_RESULT}" CompletionListener.HIT
        }

        if (result == "miss") {
          "${CompletionListener.MEMCACHED_RESULT}" CompletionListener.MISS
        }
      }
    }
  }
}
