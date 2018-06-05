import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import io.lettuce.core.ConnectionFuture
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisFuture
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.protocol.AsyncCommand
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class LettuceAsyncClientTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.lettuce.enabled", "true")
  }

  @Shared
  public static final String HOST = "127.0.0.1"
  public static final int PORT = TestUtils.randomOpenPort()
  public static final int INCORRECT_PORT = TestUtils.randomOpenPort()
  public static final int DB_INDEX = 0
  @Shared
  public static final String DB_ADDR = HOST + ":" + PORT + "/" + DB_INDEX
  @Shared
  public static final String DB_ADDR_NON_EXISTENT = HOST + ":" + INCORRECT_PORT + "/" + DB_INDEX
  @Shared
  public static final String DB_URI_NON_EXISTENT = "redis://" + DB_ADDR_NON_EXISTENT
  public static final String EMBEDDED_DB_URI = "redis://" + DB_ADDR

  @Shared
  RedisServer redisServer = RedisServer.builder()
  // bind to localhost to avoid firewall popup
    .setting("bind " + HOST)
  // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(PORT).build()

  @Shared
  RedisClient redisClient = RedisClient.create(EMBEDDED_DB_URI)

  @Shared
  RedisAsyncCommands<String, ?> asyncCommands = null

  @Shared
  Map<String, String> testHashMap = [
          firstname: "John",
          lastname:  "Doe",
          age:       "53"
  ]

  def setupSpec() {
    println "Using redis: $redisServer.args"
    redisServer.start()
    StatefulConnection connection = redisClient.connect()
    asyncCommands = connection.async()
  }

  def cleanupSpec() {
    redisServer.stop()
  }

  def "connect using get on ConnectionFuture"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(EMBEDDED_DB_URI)
    ConnectionFuture connectionFuture = testConnectionClient.connectAsync(StringCodec.UTF8,
      new RedisURI(HOST, PORT, 3, TimeUnit.SECONDS))
    def connection = connectionFuture.get()

    expect:
    connection != null
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "CONNECT:" + DB_ADDR
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.redis.url" DB_ADDR
            "db.redis.dbIndex" 0
            "db.type" "redis"
            "peer.hostname" HOST
            "peer.port" PORT
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

  def "connect exception inside the connection future"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(DB_URI_NON_EXISTENT)
    StatefulConnection connection = null

    when:
    ConnectionFuture connectionFuture = testConnectionClient.connectAsync(StringCodec.UTF8,
      new RedisURI(HOST, INCORRECT_PORT, 3, TimeUnit.SECONDS))
    connection = connectionFuture.get()

    then:
    connection == null
    thrown ExecutionException
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "CONNECT:" + DB_ADDR_NON_EXISTENT
          errored true

          tags {
            defaultTags()
            "component" "redis-client"
            "db.redis.url" DB_ADDR_NON_EXISTENT
            "db.redis.dbIndex" 0
            "db.type" "redis"
            errorTags CompletionException, String
            "peer.hostname" HOST
            "peer.port" INCORRECT_PORT
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

  def "set command using Future get with timeout"() {
    setup:
    RedisFuture<String> redisFuture = asyncCommands.set("TESTKEY", "TESTVAL")
    String res = redisFuture.get(3, TimeUnit.SECONDS)

    expect:
    res == "OK"
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "SET"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "key<TESTKEY> value<TESTVAL>"
            "span.kind" "client"
            "span.type" "redis"
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "GET"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "key<TESTKEY>"
            "span.kind" "client"
            "span.type" "redis"
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
    final String successStr = "KEY MISSING"
    BiFunction<String, Throwable, String> firstStage = new BiFunction<String, Throwable, String>() {
      @Override
      String apply(String res, Throwable throwable) {
        conds.evaluate{
          assert res == null
          assert throwable == null
        }
        return (res == null ? successStr : res)
      }
    }
    Function<String, Object> secondStage = new Function<String, Object>() {
      @Override
      Object apply(String input) {
        conds.evaluate{
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "GET"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "key<NON_EXISTENT_KEY>"
            "span.kind" "client"
            "span.type" "redis"
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
        conds.evaluate{
          assert keyRetrieved == "TESTKEY"
        }
      }
    }

    when:
    RedisFuture<String> redisFuture = asyncCommands.randomkey()
    redisFuture.whenCompleteAsync(biConsumer)

    then:
    conds.await()
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "RANDOMKEY"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

  def "hash set and then nest apply to hash getall"() {
    setup:
    def conds = new AsyncConditions()

    when:
    RedisFuture<String> hmsetFuture = asyncCommands.hmset("user", testHashMap)
    hmsetFuture.thenApplyAsync(new Function<String, Object>() {
      @Override
      Object apply(String setResult) {
        conds.evaluate {
          assert setResult == "OK"
        }
        RedisFuture<Map<String, String>> hmGetAllFuture = asyncCommands.hgetall("user")
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
    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "HMSET"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "key<user> key<firstname> value<John> key<lastname> value<Doe> key<age> value<53>"
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "HGETALL"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "key<user>"
            "span.kind" "client"
            "span.type" "redis"
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
    redisFuture.exceptionally ({
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "DEL"
          errored true

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "key<key1> key<key2>"
            errorTags(IllegalStateException, "TestException")
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }

    cleanup:
    asyncCommands.setAutoFlushCommands(true)
  }

  def "cancel command before it finishes"() {
    setup:
    asyncCommands.setAutoFlushCommands(false)
    def conds = new AsyncConditions()
    RedisFuture redisFuture = asyncCommands.sadd("SKEY", "1", "2")
    redisFuture.whenCompleteAsync({
      res, throwable -> conds.evaluate {
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
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "SADD"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "key<SKEY> value<1> value<2>"
            "db.command.cancelled" true
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }

    cleanup:
    asyncCommands.setAutoFlushCommands(true)
  }

  def "debug segfault command (returns void) with no argument should produce span"() {
    setup:
    asyncCommands.debugSegfault()

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "DEBUG"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "SEGFAULT"
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }

    cleanup:
    if (!redisServer.active) {
      redisServer.start()
    }
  }


  def "shutdown command (returns void) should produce a span"() {
    setup:
    asyncCommands.shutdown(false)

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "SHUTDOWN"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "NOSAVE"
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }

    cleanup:
    if (!redisServer.active) {
      redisServer.start()
    }
  }
}
