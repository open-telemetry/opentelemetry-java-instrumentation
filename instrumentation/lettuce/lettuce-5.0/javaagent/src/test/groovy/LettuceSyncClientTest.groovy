/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.sync.RedisCommands
import io.netty.channel.AbstractChannel
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR

class LettuceSyncClientTest extends AgentInstrumentationSpecification {
  public static final int DB_INDEX = 0
  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)

  @Shared
  String host
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

    host = redisServer.getHost()
    port = redisServer.getMappedPort(6379)
    dbAddr = host + ":" + port + "/" + DB_INDEX
    embeddedDbUri = "redis://" + dbAddr

    incorrectPort = PortUtils.findOpenPort()
    dbAddrNonExistent = host + ":" + incorrectPort + "/" + DB_INDEX
    dbUriNonExistent = "redis://" + dbAddrNonExistent

    redisClient = RedisClient.create(embeddedDbUri)

    connection = redisClient.connect()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")
    syncCommands.hmset("TESTHM", testHashMap)

    // 2 sets + 1 connect trace
    ignoreTracesAndClear(3)
  }

  def cleanup() {
    connection.close()
    redisServer.stop()
  }

  def "connect"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri)
    testConnectionClient.setOptions(CLIENT_OPTIONS)

    when:
    StatefulConnection connection = testConnectionClient.connect()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "CONNECT"
          kind CLIENT
          attributes {
            "$SemanticAttributes.NET_PEER_NAME" host
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_SYSTEM" "redis"
          }
        }
      }
    }

    cleanup:
    connection.close()
  }

  def "connect exception"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent)
    testConnectionClient.setOptions(CLIENT_OPTIONS)

    when:
    testConnectionClient.connect()

    then:
    thrown RedisConnectionException
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "CONNECT"
          kind CLIENT
          status ERROR
          errorEvent AbstractChannel.AnnotatedConnectException, String
          attributes {
            "$SemanticAttributes.NET_PEER_NAME" host
            "$SemanticAttributes.NET_PEER_PORT" incorrectPort
            "$SemanticAttributes.DB_SYSTEM" "redis"
          }
        }
      }
    }
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SET TESTSETKEY ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
          }
        }
      }
    }
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "GET TESTKEY"
            "$SemanticAttributes.DB_OPERATION" "GET"
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "GET NON_EXISTENT_KEY"
            "$SemanticAttributes.DB_OPERATION" "GET"
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "RANDOMKEY"
            "$SemanticAttributes.DB_OPERATION" "RANDOMKEY"
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "LPUSH TESTLIST ?"
            "$SemanticAttributes.DB_OPERATION" "LPUSH"
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "HMSET user firstname ? lastname ? age ?"
            "$SemanticAttributes.DB_OPERATION" "HMSET"
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "HGETALL TESTHM"
            "$SemanticAttributes.DB_OPERATION" "HGETALL"
          }
        }
      }
    }
  }

  def "debug segfault command (returns void) with no argument should produce span"() {
    setup:
    syncCommands.debugSegfault()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "DEBUG"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "DEBUG SEGFAULT"
            "$SemanticAttributes.DB_OPERATION" "DEBUG"
          }
        }
      }
    }
  }

  def "shutdown command (returns void) should produce a span"() {
    setup:
    syncCommands.shutdown(false)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SHUTDOWN"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SHUTDOWN NOSAVE"
            "$SemanticAttributes.DB_OPERATION" "SHUTDOWN"
          }
        }
      }
    }
  }
}
