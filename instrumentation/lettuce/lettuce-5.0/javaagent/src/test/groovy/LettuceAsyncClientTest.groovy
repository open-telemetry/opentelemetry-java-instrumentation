/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import io.lettuce.core.ClientOptions
import io.lettuce.core.ConnectionFuture
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisFuture
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.protocol.AsyncCommand
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

class LettuceAsyncClientTest extends AgentInstrumentationSpecification {
  public static final String PEER_NAME = "localhost"
  public static final String PEER_IP = "127.0.0.1"
  public static final int DB_INDEX = 0
  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

  @Shared
  int port
  @Shared
  int incorrectPort
  @Shared
  String dbAddr
  @Shared
  String dbAddrNonExistent
  @Shared
  String dbUriNonExistent
  @Shared
  String embeddedDbUri

  @Shared
  RedisServer redisServer

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

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    incorrectPort = PortUtils.randomOpenPort()
    dbAddr = PEER_NAME + ":" + port + "/" + DB_INDEX
    dbAddrNonExistent = PEER_NAME + ":" + incorrectPort + "/" + DB_INDEX
    dbUriNonExistent = "redis://" + dbAddrNonExistent
    embeddedDbUri = "redis://" + dbAddr

    redisServer = RedisServer.builder()
    // bind to localhost to avoid firewall popup
      .setting("bind " + PEER_NAME)
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
    asyncCommands = connection.async()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")

    // 1 set + 1 connect trace
    ignoreTracesAndClear(2)
  }

  def cleanup() {
    connection.close()
    redisServer.stop()
  }

  def "connect using get on ConnectionFuture"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri)
    testConnectionClient.setOptions(CLIENT_OPTIONS)

    when:
    ConnectionFuture connectionFuture = testConnectionClient.connectAsync(StringCodec.UTF8,
      new RedisURI(PEER_NAME, port, 3, TimeUnit.SECONDS))
    StatefulConnection connection = connectionFuture.get()

    then:
    connection != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "CONNECT"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.NET_PEER_NAME.key" PEER_NAME
            "$SemanticAttributes.NET_PEER_IP.key" PEER_IP
            "$SemanticAttributes.NET_PEER_PORT.key" port
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "CONNECT"
          }
        }
      }
    }

    cleanup:
    connection.close()
  }

  def "connect exception inside the connection future"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent)
    testConnectionClient.setOptions(CLIENT_OPTIONS)

    when:
    ConnectionFuture connectionFuture = testConnectionClient.connectAsync(StringCodec.UTF8,
      new RedisURI(PEER_NAME, incorrectPort, 3, TimeUnit.SECONDS))
    StatefulConnection connection = connectionFuture.get()

    then:
    connection == null
    thrown ExecutionException
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "CONNECT"
          kind CLIENT
          errored true
          errorEvent CompletionException, String
          attributes {
            "$SemanticAttributes.NET_PEER_NAME.key" PEER_NAME
            "$SemanticAttributes.NET_PEER_IP.key" PEER_IP
            "$SemanticAttributes.NET_PEER_PORT.key" incorrectPort
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "CONNECT"
          }
        }
      }
    }
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
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "SET TESTSETKEY ?"
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
        conds.evaluate {
          assert res == "TESTVAL"
        }
      }
    }

    when:
    RedisFuture<String> redisFuture = asyncCommands.get("TESTKEY")
    redisFuture.thenAccept(consumer)

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "GET"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "GET TESTKEY"
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
        conds.evaluate {
          assert res == null
          assert throwable == null
        }
        return (res == null ? successStr : res)
      }
    }
    Function<String, Object> secondStage = new Function<String, Object>() {
      @Override
      Object apply(String input) {
        conds.evaluate {
          assert input == successStr
        }
        return null
      }
    }

    when:
    RedisFuture<String> redisFuture = asyncCommands.get("NON_EXISTENT_KEY")
    redisFuture.handleAsync(firstStage).thenApply(secondStage)

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "GET"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "GET NON_EXISTENT_KEY"
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
        conds.evaluate {
          assert keyRetrieved != null
        }
      }
    }

    when:
    RedisFuture<String> redisFuture = asyncCommands.randomkey()
    redisFuture.whenCompleteAsync(biConsumer)

    then:
    conds.await()
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "RANDOMKEY"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "RANDOMKEY"
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
    conds.await()
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "HMSET"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "HMSET TESTHM firstname ? lastname ? age ?"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "HGETALL"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "HGETALL TESTHM"
          }
        }
      }
    }
  }

  def "command completes exceptionally"() {
    setup:
    // turn off auto flush to complete the command exceptionally manually
    asyncCommands.setAutoFlushCommands(false)
    def conds = new AsyncConditions()
    RedisFuture redisFuture = asyncCommands.del("key1", "key2")
    boolean completedExceptionally = ((AsyncCommand) redisFuture).completeExceptionally(new IllegalStateException("TestException"))
    redisFuture.exceptionally({
      throwable ->
        conds.evaluate {
          assert throwable != null
          assert throwable instanceof IllegalStateException
          assert throwable.getMessage() == "TestException"
        }
        throw throwable
    })

    when:
    // now flush and execute the command
    asyncCommands.flushCommands()
    redisFuture.get()

    then:
    conds.await()
    completedExceptionally == true
    thrown Exception
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "DEL"
          kind CLIENT
          errored true
          errorEvent(IllegalStateException, "TestException")
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "DEL key1 key2"
          }
        }
      }
    }
  }

  def "cancel command before it finishes"() {
    setup:
    asyncCommands.setAutoFlushCommands(false)
    def conds = new AsyncConditions()
    RedisFuture redisFuture = asyncCommands.sadd("SKEY", "1", "2")
    redisFuture.whenCompleteAsync({
      res, throwable ->
        conds.evaluate {
          assert throwable != null
          assert throwable instanceof CancellationException
        }
    })

    when:
    boolean cancelSuccess = redisFuture.cancel(true)
    asyncCommands.flushCommands()

    then:
    conds.await()
    cancelSuccess == true
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SADD"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "SADD SKEY ? ?"
            "lettuce.command.cancelled" true
          }
        }
      }
    }
  }

  def "debug segfault command (returns void) with no argument should produce span"() {
    setup:
    asyncCommands.debugSegfault()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "DEBUG"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "DEBUG SEGFAULT"
          }
        }
      }
    }
  }


  def "shutdown command (returns void) should produce a span"() {
    setup:
    asyncCommands.shutdown(false)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SHUTDOWN"
          kind CLIENT
          errored false
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.DB_STATEMENT.key" "SHUTDOWN NOSAVE"
          }
        }
      }
    }
  }
}
