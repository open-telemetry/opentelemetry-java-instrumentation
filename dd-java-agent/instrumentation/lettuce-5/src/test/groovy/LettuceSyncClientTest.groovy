import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.sync.RedisCommands
import redis.embedded.RedisServer
import spock.lang.Shared

import static datadog.trace.instrumentation.lettuce.ConnectionFutureAdvice.RESOURCE_NAME_PREFIX
import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class LettuceSyncClientTest extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.redis.enabled", "true")
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
  RedisCommands<String, ?> syncCommands = null

  @Shared
  Map<String, String> testHashMap = [
          firstname: "John",
          lastname:  "Doe",
          age:       "53"
  ]

  def setupSpec() {
    redisServer.start()
    StatefulConnection connection = redisClient.connect()
    syncCommands = connection.sync()
  }

  def cleanupSpec() {
    redisServer.stop()
  }

  def "connect"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(EMBEDDED_DB_URI)
    testConnectionClient.connect()
    TEST_WRITER.waitForTraces(1)

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName RESOURCE_NAME_PREFIX + DB_ADDR
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

  def "connect exception"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(DB_URI_NON_EXISTENT)
    try {
      testConnectionClient.connect()
    } catch (RedisConnectionException rce) { }
    TEST_WRITER.waitForTraces(1)

    expect:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName RESOURCE_NAME_PREFIX + DB_ADDR_NON_EXISTENT
          errored true

          tags {
            defaultTags()
            "component" "redis-client"
            "db.redis.url" DB_ADDR_NON_EXISTENT
            "db.redis.dbIndex" 0
            "db.type" "redis"
            errorTags(RedisConnectionException, "some error due to incorrect port number")
            "peer.hostname" HOST
            "peer.port" INCORRECT_PORT
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

  def "set command"() {
    setup:
    String res = syncCommands.set("TESTKEY", "TESTVAL")
    TEST_WRITER.waitForTraces(1)

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

  def "get command"() {
    setup:
    String res = syncCommands.get("TESTKEY")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == "TESTVAL"
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

  def "get non existent key command"() {
    setup:
    String res = syncCommands.get("NON_EXISTENT_KEY")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == null
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

  def "command with no arguments"() {
    setup:
    def keyRetrieved = syncCommands.randomkey()
    TEST_WRITER.waitForTraces(1)

    expect:
    keyRetrieved == "TESTKEY"
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

  def "list command"() {
    setup:
    long res = syncCommands.lpush("TESTLIST", "TESTLIST ELEMENT")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == 1
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType "redis"
          resourceName "LPUSH"
          errored false

          tags {
            defaultTags()
            "component" "redis-client"
            "db.type" "redis"
            "db.command.args" "key<TESTLIST> value<TESTLIST ELEMENT>"
            "span.kind" "client"
            "span.type" "redis"
          }
        }
      }
    }
  }

  def "hash set command"() {
    setup:
    def res = syncCommands.hmset("user", testHashMap)
    TEST_WRITER.waitForTraces(1)

    expect:
    res == "OK"
    assertTraces(TEST_WRITER, 1) {
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
    }
  }

  def "hash getall command"() {
    setup:
    Map<String, String> res = syncCommands.hgetall("user")
    TEST_WRITER.waitForTraces(1)

    expect:
    res == testHashMap
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
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
}
