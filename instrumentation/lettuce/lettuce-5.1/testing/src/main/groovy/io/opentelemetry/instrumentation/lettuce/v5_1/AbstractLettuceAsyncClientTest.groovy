/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1

import io.lettuce.core.ConnectionFuture
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisFuture
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.StringCodec
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

abstract class AbstractLettuceAsyncClientTest extends InstrumentationSpecification {
  public static final int DB_INDEX = 0
  public static final String loopback = "127.0.0.1"

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)

  abstract RedisClient createClient(String uri)

  @Shared
  String host
  @Shared
  String expectedHostAttributeValue
  @Shared
  int port
  @Shared
  int incorrectPort
  @Shared
  String dbUriNonExistent
  @Shared
  String embeddedDbUri

  @Shared
  Map<String, String> testHashMap = [
    firstname: "John",
    lastname : "Doe",
    age      : "53"
  ]

  RedisClient redisClient
  StatefulConnection connection
  RedisAsyncCommands<String, ?> asyncCommands
  RedisCommands<String, ?> syncCommands

  def setup() {
    redisServer.start()

    port = redisServer.getMappedPort(6379)
    host = redisServer.getHost()
    String dbAddr = host + ":" + port + "/" + DB_INDEX
    embeddedDbUri = "redis://" + dbAddr
    expectedHostAttributeValue = host == loopback ? null : host

    incorrectPort = PortUtils.findOpenPort()
    String dbAddrNonExistent = host + ":" + incorrectPort + "/" + DB_INDEX
    dbUriNonExistent = "redis://" + dbAddrNonExistent

    redisClient = createClient(embeddedDbUri)
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS)

    connection = redisClient.connect()
    asyncCommands = connection.async()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")

    // 1 set
    ignoreTracesAndClear(1)
  }

  def cleanup() {
    connection.close()
    redisClient.shutdown()
    redisServer.stop()
  }

  boolean testCallback() {
    return true
  }

  def <T> T runWithCallbackSpan(String spanName, Closure callback) {
    if (testCallback()) {
      return runWithSpan(spanName, callback)
    }
    return callback.call()
  }

  def "connect using get on ConnectionFuture"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri)
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS)

    when:
    ConnectionFuture connectionFuture = testConnectionClient.connectAsync(StringCodec.UTF8,
      RedisURI.create("redis://${host}:${port}?timeout=3s"))
    StatefulConnection connection = connectionFuture.get()

    then:
    connection != null
    // Lettuce tracing does not trace connect
    assertTraces(0) {}

    cleanup:
    connection.close()
    testConnectionClient.shutdown()
  }

  def "connect exception inside the connection future"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent)
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS)

    when:
    ConnectionFuture connectionFuture = testConnectionClient.connectAsync(StringCodec.UTF8,
      RedisURI.create("redis://${host}:${incorrectPort}?timeout=3s"))
    StatefulConnection connection = connectionFuture.get()

    then:
    connection == null
    thrown ExecutionException
    // Lettuce tracing does not trace connect
    assertTraces(0) {}

    cleanup:
    testConnectionClient.shutdown()
  }

  def "set command using Future get with timeout"() {
    setup:
    RedisFuture<String> redisFuture = asyncCommands.set("TESTSETKEY", "TESTSETVAL")
    String res = redisFuture.get(3, TimeUnit.SECONDS)

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "SET TESTSETKEY ?"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
      }
    }
  }

  def "get command chained with thenAccept"() {
    setup:
    def conds = new AsyncConditions()
    Consumer<String> consumer = new Consumer<String>() {
      @Override
      void accept(String res) {
        runWithCallbackSpan("callback") {
          conds.evaluate {
            assert res == "TESTVAL"
          }
        }
      }
    }

    when:
    runWithSpan("parent") {
      RedisFuture<String> redisFuture = asyncCommands.get("TESTKEY")
      redisFuture.thenAccept(consumer)
    }

    then:
    conds.await(10)
    assertTraces(1) {
      trace(0, 2 + (testCallback() ? 1 : 0)) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "GET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "GET TESTKEY"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
        if (testCallback()) {
          span(2) {
            name "callback"
            kind INTERNAL
            childOf(span(0))
          }
        }
      }
    }
  }

  // to make sure instrumentation's chained completion stages won't interfere with user's, while still
  // recording metrics
  def "get non existent key command with handleAsync and chained with thenApply"() {
    setup:
    def conds = new AsyncConditions()
    String successStr = "KEY MISSING"
    BiFunction<String, Throwable, String> firstStage = new BiFunction<String, Throwable, String>() {
      @Override
      String apply(String res, Throwable throwable) {
        runWithCallbackSpan("callback1") {
          conds.evaluate {
            assert res == null
            assert throwable == null
          }
        }
        return (res == null ? successStr : res)
      }
    }
    Function<String, Object> secondStage = new Function<String, Object>() {
      @Override
      Object apply(String input) {
        runWithCallbackSpan("callback2") {
          conds.evaluate {
            assert input == successStr
          }
        }
        return null
      }
    }

    when:
    runWithSpan("parent") {
      RedisFuture<String> redisFuture = asyncCommands.get("NON_EXISTENT_KEY")
      redisFuture.handleAsync(firstStage).thenApply(secondStage)
    }

    then:
    conds.await(10)
    assertTraces(1) {
      trace(0, 2 + (testCallback() ? 2 : 0)) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "GET"
          kind CLIENT
          childOf(span(0))
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "GET NON_EXISTENT_KEY"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
        if (testCallback()) {
          span(2) {
            name "callback1"
            kind INTERNAL
            childOf(span(0))
          }
          span(3) {
            name "callback2"
            kind INTERNAL
            childOf(span(0))
          }
        }
      }
    }
  }

  def "command with no arguments using a biconsumer"() {
    setup:
    def conds = new AsyncConditions()
    BiConsumer<String, Throwable> biConsumer = new BiConsumer<String, Throwable>() {
      @Override
      void accept(String keyRetrieved, Throwable throwable) {
        runWithCallbackSpan("callback") {
          conds.evaluate {
            assert keyRetrieved != null
          }
        }
      }
    }

    when:
    runWithSpan("parent") {
      RedisFuture<String> redisFuture = asyncCommands.randomkey()
      redisFuture.whenCompleteAsync(biConsumer)
    }

    then:
    conds.await(10)
    assertTraces(1) {
      trace(0, 2 + (testCallback() ? 1 : 0)) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "RANDOMKEY"
          kind CLIENT
          childOf(span(0))
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_STATEMENT.key}" "RANDOMKEY"
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
        if (testCallback()) {
          span(2) {
            name "callback"
            kind INTERNAL
            childOf(span(0))
          }
        }
      }
    }
  }

  def "hash set and then nest apply to hash getall"() {
    setup:
    def conds = new AsyncConditions()

    when:
    RedisFuture<String> hmsetFuture = asyncCommands.hmset("TESTHM", testHashMap)
    hmsetFuture.thenApplyAsync(new Function<String, Object>() {
      @Override
      Object apply(String setResult) {
        conds.evaluate {
          assert setResult == "OK"
        }
        RedisFuture<Map<String, String>> hmGetAllFuture = asyncCommands.hgetall("TESTHM")
        hmGetAllFuture.exceptionally(new Function<Throwable, Map<String, String>>() {
          @Override
          Map<String, String> apply(Throwable throwable) {
            println("unexpected:" + throwable.toString())
            throwable.printStackTrace()
            assert false
            return null
          }
        })
        hmGetAllFuture.thenAccept(new Consumer<Map<String, String>>() {
          @Override
          void accept(Map<String, String> hmGetAllResult) {
            conds.evaluate {
              assert testHashMap == hmGetAllResult
            }
          }
        })
        return null
      }
    })

    then:
    conds.await(10)
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "HMSET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "HMSET TESTHM firstname ? lastname ? age ?"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "HGETALL"
          kind CLIENT
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "HGETALL TESTHM"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
      }
    }
  }
}
