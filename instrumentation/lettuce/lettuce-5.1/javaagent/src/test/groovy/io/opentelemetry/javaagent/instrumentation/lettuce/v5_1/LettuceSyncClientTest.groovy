/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static java.nio.charset.StandardCharsets.UTF_8

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.sync.RedisCommands
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import redis.embedded.RedisServer
import spock.lang.Shared

class LettuceSyncClientTest extends AgentInstrumentationSpecification {
  public static final String HOST = "127.0.0.1"
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
  String embeddedDbLocalhostUri

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
  RedisCommands<String, ?> syncCommands

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    incorrectPort = PortUtils.randomOpenPort()
    dbAddr = HOST + ":" + port + "/" + DB_INDEX
    dbAddrNonExistent = HOST + ":" + incorrectPort + "/" + DB_INDEX
    dbUriNonExistent = "redis://" + dbAddrNonExistent
    embeddedDbUri = "redis://" + dbAddr
    embeddedDbLocalhostUri = "redis://localhost:" + port + "/" + DB_INDEX

    redisServer = RedisServer.builder()
    // bind to localhost to avoid firewall popup
      .setting("bind " + HOST)
    // set max memory to avoid problems in CI
      .setting("maxmemory 128M")
      .port(port).build()
  }

  def setup() {
    redisClient = RedisClient.create(embeddedDbUri)

    redisServer.start()
    connection = redisClient.connect()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")
    syncCommands.hmset("TESTHM", testHashMap)

    // 2 sets
    ignoreTracesAndClear(2)
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
    // Lettuce tracing does not trace connect
    assertTraces(0) {}

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
    // Lettuce tracing does not trace connect
    assertTraces(0) {}
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

  def "set command localhost"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(embeddedDbLocalhostUri)
    testConnectionClient.setOptions(CLIENT_OPTIONS)
    StatefulConnection connection = testConnectionClient.connect()
    String res = connection.sync().set("TESTSETKEY", "TESTSETVAL")

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://localhost:$port"
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
    def keyRetrieved = syncCommands.randomkey()

    expect:
    keyRetrieved != null
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
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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
          errored false
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "redis://127.0.0.1:$port"
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
    // lettuce tracing does not trace debug
    assertTraces(0) {}
  }

  def "shutdown command (returns void) produces no span"() {
    setup:
    syncCommands.shutdown(false)

    expect:
    // lettuce tracing does not trace shutdown
    assertTraces(0) {}
  }
}
