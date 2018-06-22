import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import io.lettuce.core.*
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.reactive.RedisReactiveCommands
import io.lettuce.core.api.sync.RedisCommands
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

import java.util.function.Consumer

import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil.AGENT_CRASHING_COMMAND_PREFIX

class LettuceReactiveClientTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.lettuce.enabled", "true")
  }

  public static final String HOST = "127.0.0.1"
  public static final int PORT = TestUtils.randomOpenPort()
  public static final int DB_INDEX = 0
  public static final String DB_ADDR = HOST + ":" + PORT + "/" + DB_INDEX
  public static final String EMBEDDED_DB_URI = "redis://" + DB_ADDR
  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

  @Shared
  RedisServer redisServer = RedisServer.builder()
    // bind to localhost to avoid firewall popup
    .setting("bind " + HOST)
    // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(PORT).build()

  RedisClient redisClient = RedisClient.create(EMBEDDED_DB_URI)
  StatefulConnection connection
  RedisReactiveCommands<String, ?> reactiveCommands
  RedisCommands<String, ?> syncCommands

  def setup() {
    println "Using redis: $redisServer.args"
    redisServer.start()
    redisClient.setOptions(CLIENT_OPTIONS)

    connection = redisClient.connect()
    reactiveCommands = connection.reactive()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")

    // 1 set + 1 connect trace
    TEST_WRITER.waitForTraces(2)
    TEST_WRITER.clear()
  }

  def cleanup() {
    connection.close()
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
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

  def "get command with lambda function"() {
    setup:
    def conds = new AsyncConditions()

    when:
    reactiveCommands.get("TESTKEY").subscribe { res -> conds.evaluate { assert res == "TESTVAL"} }

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
            "span.kind" "client"
            "span.type" "redis"
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
      res -> conds.evaluate {
        assert res == defaultVal
      }
    }

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
            "span.kind" "client"
            "span.type" "redis"
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
      res -> conds.evaluate {
        assert res == "TESTKEY"
      }
    }

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

  def "command flux publisher "() {
    setup:
    reactiveCommands.command().subscribe()

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName AGENT_CRASHING_COMMAND_PREFIX + "COMMAND"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.results.count" 157
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

  def "command cancel after 2 on flux publisher "() {
    setup:
    reactiveCommands.command().take(2).subscribe()

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName AGENT_CRASHING_COMMAND_PREFIX + "COMMAND"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.cancelled" true
            "db.command.results.count" 2
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

  def "non reactive command should not produce span"() {
    setup:
    String res = null

    when:
    res = reactiveCommands.digest()

    then:
    res != null
    TEST_WRITER.size() == 0
  }

  def "debug segfault command (returns mono void) with no argument should produce span"() {
    setup:
    reactiveCommands.debugSegfault().subscribe()

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName AGENT_CRASHING_COMMAND_PREFIX + "DEBUG"
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

  def "shutdown command (returns void) with argument should produce span"() {
    setup:
    reactiveCommands.shutdown(false).subscribe()

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
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

}
