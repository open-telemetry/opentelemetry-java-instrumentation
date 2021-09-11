/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.api.sync.RedisCommands
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.testcontainers.containers.FixedHostPortGenericContainer
import reactor.core.scheduler.Schedulers
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

import java.util.function.Consumer

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class LettuceReactiveClientTest extends AgentInstrumentationSpecification {
  public static final String PEER_HOST = "localhost"
  public static final String PEER_IP = "127.0.0.1"
  public static final int DB_INDEX = 0
  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

  private static FixedHostPortGenericContainer redisServer = new FixedHostPortGenericContainer<>("redis:6.2.3-alpine")

  @Shared
  String embeddedDbUri


  RedisClient redisClient
  StatefulConnection connection
  RedisReactiveCommands<String, ?> reactiveCommands
  RedisCommands<String, ?> syncCommands

  def setupSpec() {
    int port = PortUtils.findOpenPort()
    String dbAddr = PEER_HOST + ":" + port + "/" + DB_INDEX
    embeddedDbUri = "redis://" + dbAddr

    redisServer = redisServer.withFixedExposedPort(port, 6379)
  }

  def setup() {
    redisClient = RedisClient.create(embeddedDbUri)

    redisServer.start()
    redisClient.setOptions(CLIENT_OPTIONS)

    connection = redisClient.connect()
    reactiveCommands = connection.reactive()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")

    // 1 set + 1 connect trace
    ignoreTracesAndClear(2)
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
        runWithSpan("callback") {
          conds.evaluate {
            assert res == "OK"
          }
        }
      }
    }

    when:
    runWithSpan("parent") {
      reactiveCommands.set("TESTSETKEY", "TESTSETVAL").subscribe(consumer)
    }

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "SET"
          kind CLIENT
          childOf(span(0))
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "SET TESTSETKEY ?"
            "$SemanticAttributes.DB_OPERATION.key" "SET"
          }
        }
        span(2) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
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
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "GET TESTKEY"
            "$SemanticAttributes.DB_OPERATION.key" "GET"
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
    runWithSpan("parent") {
      reactiveCommands.get("NON_EXISTENT_KEY").defaultIfEmpty(defaultVal).subscribe {
        res ->
          runWithSpan("callback") {
            conds.evaluate {
              assert res == defaultVal
            }
          }
      }
    }

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 3) {
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
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "GET NON_EXISTENT_KEY"
            "$SemanticAttributes.DB_OPERATION.key" "GET"
          }
        }
        span(2) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
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
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "RANDOMKEY"
            "$SemanticAttributes.DB_OPERATION.key" "RANDOMKEY"
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
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "COMMAND"
            "$SemanticAttributes.DB_OPERATION.key" "COMMAND"
            "lettuce.command.results.count" { it > 100 }
          }
        }
      }
    }
  }

  def "command cancel after 2 on flux publisher "() {
    setup:
    reactiveCommands.command().take(2).subscribe()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "COMMAND"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "COMMAND"
            "$SemanticAttributes.DB_OPERATION.key" "COMMAND"
            "lettuce.command.cancelled" true
            "lettuce.command.results.count" 2
          }
        }
      }
    }
  }

  def "non reactive command should not produce span"() {
    when:
    def res = reactiveCommands.digest(null)

    then:
    res != null
    traces.size() == 0
  }

  def "debug segfault command (returns mono void) with no argument should produce span"() {
    setup:
    reactiveCommands.debugSegfault().subscribe()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "DEBUG"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "DEBUG SEGFAULT"
            "$SemanticAttributes.DB_OPERATION.key" "DEBUG"
          }
        }
      }
    }
  }

  def "shutdown command (returns void) with argument should produce span"() {
    setup:
    reactiveCommands.shutdown(false).subscribe()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SHUTDOWN"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "SHUTDOWN NOSAVE"
            "$SemanticAttributes.DB_OPERATION.key" "SHUTDOWN"
          }
        }
      }
    }
  }

  def "blocking subscriber"() {
    when:
    runWithSpan("test-parent") {
      reactiveCommands.set("a", "1")
        .then(reactiveCommands.get("a"))
        .block()
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "test-parent"
          attributes {
          }
        }
        span(1) {
          name "SET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "SET a ?"
            "$SemanticAttributes.DB_OPERATION.key" "SET"
          }
        }
        span(2) {
          name "GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "GET a"
            "$SemanticAttributes.DB_OPERATION.key" "GET"
          }
        }
      }
    }
  }

  def "async subscriber"() {
    when:
    runWithSpan("test-parent") {
      reactiveCommands.set("a", "1")
        .then(reactiveCommands.get("a"))
        .subscribe()
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "test-parent"
          attributes {
          }
        }
        span(1) {
          name "SET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "SET a ?"
            "$SemanticAttributes.DB_OPERATION.key" "SET"
          }
        }
        span(2) {
          name "GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "GET a"
            "$SemanticAttributes.DB_OPERATION.key" "GET"
          }
        }
      }
    }
  }

  def "async subscriber with specific thread pool"() {
    when:
    runWithSpan("test-parent") {
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
          attributes {
          }
        }
        span(1) {
          name "SET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "SET a ?"
            "$SemanticAttributes.DB_OPERATION.key" "SET"
          }
        }
        span(2) {
          name "GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "GET a"
            "$SemanticAttributes.DB_OPERATION.key" "GET"
          }
        }
      }
    }
  }
}
