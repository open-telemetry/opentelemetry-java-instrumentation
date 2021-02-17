/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.api.sync.RedisCommands
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.function.Consumer
import reactor.core.scheduler.Schedulers
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

class LettuceReactiveClientTest extends AgentInstrumentationSpecification {
  public static final String HOST = "127.0.0.1"
  public static final int DB_INDEX = 0
  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

  @Shared
  int port
  @Shared
  String embeddedDbUri

  @Shared
  RedisServer redisServer

  RedisClient redisClient
  StatefulConnection connection
  RedisReactiveCommands<String, ?> reactiveCommands
  RedisCommands<String, ?> syncCommands

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    String dbAddr = HOST + ":" + port + "/" + DB_INDEX
    embeddedDbUri = "redis://" + dbAddr

    redisServer = RedisServer.builder()
    // bind to localhost to avoid firewall popup
      .setting("bind " + HOST)
    // set max memory to avoid problems in CI
      .setting("maxmemory 128M")
      .port(port).build()
  }

  def setup() {
    redisClient = RedisClient.create(embeddedDbUri)

    println "Using redis: $redisServer.args"
    redisServer.start()
    redisClient.setOptions(CLIENT_OPTIONS)

    connection = redisClient.connect()
    reactiveCommands = connection.reactive()
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

  def "set command with subscribe on a defined consumer"() {
    setup:
    def conds = new AsyncConditions()
    Consumer<String> consumer = new Consumer<String>() {
      @Override
      void accept(String res) {
        conds.evaluate {
          assert res == "OK"
        }
      }
    }

    when:
    reactiveCommands.set("TESTSETKEY", "TESTSETVAL").subscribe(consumer)

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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

  def "get command with lambda function"() {
    setup:
    def conds = new AsyncConditions()

    when:
    reactiveCommands.get("TESTKEY").subscribe { res -> conds.evaluate { assert res == "TESTVAL" } }

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "GET"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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
      }
    }
  }

  // to make sure instrumentation's chained completion stages won't interfere with user's, while still
  // recording metrics
  def "get non existent key command"() {
    setup:
    def conds = new AsyncConditions()
    final defaultVal = "NOT THIS VALUE"

    when:
    reactiveCommands.get("NON_EXISTENT_KEY").defaultIfEmpty(defaultVal).subscribe {
      res ->
        conds.evaluate {
          assert res == defaultVal
        }
    }

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "GET"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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
      }
    }

  }

  def "command with no arguments"() {
    setup:
    def conds = new AsyncConditions()

    when:
    reactiveCommands.randomkey().subscribe {
      res ->
        conds.evaluate {
          assert res == "TESTKEY"
        }
    }

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "RANDOMKEY"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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
      }
    }
  }

  def "command flux publisher "() {
    setup:
    reactiveCommands.command().subscribe()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "COMMAND"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
            "${SemanticAttributes.DB_STATEMENT.key}" "COMMAND"
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
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

  def "non reactive command should not produce span"() {
    when:
    def res = reactiveCommands.digest()

    then:
    res != null
    traces.size() == 0
  }

  def "blocking subscriber"() {
    when:
    runUnderTrace("test-parent") {
      reactiveCommands.set("a", "1")
        .then(reactiveCommands.get("a"))
        .block()
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "test-parent"
          errored false
          attributes {
          }
        }
        span(1) {
          name "SET"
          kind CLIENT
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "SET a ?"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
        span(2) {
          name "GET"
          kind CLIENT
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "GET a"
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

  def "async subscriber"() {
    when:
    runUnderTrace("test-parent") {
      reactiveCommands.set("a", "1")
        .then(reactiveCommands.get("a"))
        .subscribe()
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "test-parent"
          errored false
          attributes {
          }
        }
        span(1) {
          name "SET"
          kind CLIENT
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "SET a ?"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
        span(2) {
          name "GET"
          kind CLIENT
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "GET a"
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

  def "async subscriber with specific thread pool"() {
    when:
    runUnderTrace("test-parent") {
      reactiveCommands.set("a", "1")
        .then(reactiveCommands.get("a"))
        .subscribeOn(Schedulers.elastic())
        .subscribe()
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "test-parent"
          errored false
          attributes {
          }
        }
        span(1) {
          name "SET"
          kind CLIENT
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "SET a ?"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
        }
        span(2) {
          name "GET"
          kind CLIENT
          errored false
          childOf span(0)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "GET a"
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
