/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.RedisException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.sync.RedisCommands
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP
import static java.nio.charset.StandardCharsets.UTF_8

abstract class AbstractLettuceSyncClientTest extends InstrumentationSpecification {
  public static final int DB_INDEX = 0
  public static final String loopback = "127.0.0.1"

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)

  abstract RedisClient createClient(String uri)

  @Shared
  String expectedHostAttributeValue
  @Shared
  int port
  @Shared
  String dbUriNonExistent
  @Shared
  String embeddedDbLocalhostUri

  @Shared
  Map<String, String> testHashMap = [
    firstname: "John",
    lastname : "Doe",
    age      : "53"
  ]

  RedisClient redisClient
  StatefulConnection connection
  RedisCommands<String, ?> syncCommands

  def setup() {
    redisServer.start()

    port = redisServer.getMappedPort(6379)
    String host = redisServer.getHost()
    String dbAddr = host + ":" + port + "/" + DB_INDEX
    String embeddedDbUri = "redis://" + dbAddr
    embeddedDbLocalhostUri = "redis://localhost:" + port + "/" + DB_INDEX
    expectedHostAttributeValue = host == loopback ? null : host

    int incorrectPort = PortUtils.findOpenPort()
    String dbAddrNonExistent = host + ":" + incorrectPort + "/" + DB_INDEX
    dbUriNonExistent = "redis://" + dbAddrNonExistent

    redisClient = createClient(embeddedDbUri)
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS)

    connection = redisClient.connect()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")
    syncCommands.hmset("TESTHM", testHashMap)

    // 2 sets
    ignoreTracesAndClear(2)
  }

  def cleanup() {
    connection.close()
    redisClient.shutdown()
    redisServer.stop()
  }

  def "connect"() {
//    setup:
//    RedisClient testConnectionClient = createClient(embeddedDbUri)
//    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS)

    when:
    StatefulConnection connection = redisClient.connect()

    then:
    // Lettuce tracing does not trace connect
    assertTraces(0) {}

    cleanup:
    connection.close()
//    testConnectionClient.shutdown()
  }

  def "connect exception"() {
    setup:
    RedisClient testConnectionClient = createClient(dbUriNonExistent)
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS)

    when:
    testConnectionClient.connect()

    then:
    thrown RedisConnectionException
    // Lettuce tracing does not trace connect
    assertTraces(0) {}

    cleanup:
    testConnectionClient.shutdown()
  }

  def "set command"() {
    setup:
    String res = syncCommands.set("TESTSETKEY", "TESTSETVAL")

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

  def "set command localhost"() {
    setup:
    RedisClient testConnectionClient = createClient(embeddedDbLocalhostUri)
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS)
    StatefulConnection connection = testConnectionClient.connect()
    String res = connection.sync().set("TESTSETKEY", "TESTSETVAL")

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
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

    cleanup:
    connection.close()
    testConnectionClient.shutdown()
  }

  def "get command"() {
    setup:
    String res = syncCommands.get("TESTKEY")

    expect:
    res == "TESTVAL"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
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
      }
    }
  }

  def "get non existent key command"() {
    setup:
    String res = syncCommands.get("NON_EXISTENT_KEY")

    expect:
    res == null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "GET"
          kind CLIENT
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
      }
    }
  }

  def "command with no arguments"() {
    setup:
    def keyRetrieved = syncCommands.randomkey()

    expect:
    keyRetrieved != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "RANDOMKEY"
          kind CLIENT
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
      }
    }
  }

  def "list command"() {
    setup:
    long res = syncCommands.lpush("TESTLIST", "TESTLIST ELEMENT")

    expect:
    res == 1
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "LPUSH"
          kind CLIENT
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "LPUSH TESTLIST ?"
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

  def "hash set command"() {
    setup:
    def res = syncCommands.hmset("user", testHashMap)

    expect:
    res == "OK"
    assertTraces(1) {
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
            "${SemanticAttributes.DB_STATEMENT.key}" "HMSET user firstname ? lastname ? age ?"
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

  def "hash getall command"() {
    setup:
    Map<String, String> res = syncCommands.hgetall("TESTHM")

    expect:
    res == testHashMap
    assertTraces(1) {
      trace(0, 1) {
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

  def "eval command"() {
    given:
    def script = "redis.call('lpush', KEYS[1], ARGV[1], ARGV[2]); return redis.call('llen', KEYS[1])"

    when:
    def result = syncCommands.eval(script, ScriptOutputType.INTEGER, ["TESTLIST"] as String[], "abc", "def")

    then:
    result == 2

    def b64Script = Base64.encoder.encodeToString(script.getBytes(UTF_8))
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "EVAL"
          kind CLIENT
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "EVAL $b64Script 1 TESTLIST ? ?"
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

  def "mset command"() {
    when:
    def res = syncCommands.mset([
      "key1": "value1",
      "key2": "value2"
    ])

    then:
    res == "OK"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "MSET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "MSET key1 ? key2 ?"
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

  def "debug segfault command (returns void) with no argument produces no span"() {
    setup:
    syncCommands.debugSegfault()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "DEBUG"
          kind CLIENT
          // Disconnect not an actual error even though an exception is recorded.
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "DEBUG SEGFAULT"
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
          if (Boolean.getBoolean("testLatestDeps")) {
            // Seems to only be recorded with Lettuce 6+
            errorEvent(RedisException, "Connection disconnected", 2)
          }
        }
      }
    }
  }

  def "shutdown command (returns void) produces no span"() {
    setup:
    syncCommands.shutdown(false)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SHUTDOWN"
          kind CLIENT
          if (Boolean.getBoolean("testLatestDeps")) {
            // Seems to only be treated as an error with Lettuce 6+
            status ERROR
          }
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" expectedHostAttributeValue
            "${SemanticAttributes.NET_PEER_IP.key}" loopback
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "SHUTDOWN NOSAVE"
            if (!Boolean.getBoolean("testLatestDeps")) {
              // Lettuce adds this tag before 6.0
              // TODO(anuraaga): Filter this out?
              "error" "Connection disconnected"
            }
          }
          event(0) {
            eventName "redis.encode.start"
          }
          event(1) {
            eventName "redis.encode.end"
          }
          if (Boolean.getBoolean("testLatestDeps")) {
            errorEvent(RedisException, "Connection disconnected", 2)
          }
        }
      }
    }
  }
}
