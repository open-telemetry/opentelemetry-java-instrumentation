/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.lettuce.v5_1

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.sync.RedisCommands
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import redis.embedded.RedisServer
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT

class LettuceSyncClientTest extends AgentTestRunner {
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
    TEST_WRITER.waitForTraces(2)
    TEST_WRITER.clear()
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
          operationName "SET"
          spanKind CLIENT
          errored false
          attributes {
            SemanticAttributes.NET_TRANSPORT.key() "IP.TCP"
            SemanticAttributes.NET_PEER_IP.key() "127.0.0.1"
            SemanticAttributes.NET_PEER_PORT.key() port
            "db.connection_string" "redis://127.0.0.1:$port"
            "db.system" "redis"
            SemanticAttributes.DB_STATEMENT.key() "SET key<TESTSETKEY> value<TESTSETVAL>"
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
          operationName "SET"
          spanKind CLIENT
          errored false
          attributes {
            SemanticAttributes.NET_TRANSPORT.key() "IP.TCP"
            SemanticAttributes.NET_PEER_IP.key() "127.0.0.1"
            "net.peer.name" "localhost"
            SemanticAttributes.NET_PEER_PORT.key() port
            "db.connection_string" "redis://localhost:$port"
            "db.system" "redis"
            SemanticAttributes.DB_STATEMENT.key() "SET key<TESTSETKEY> value<TESTSETVAL>"
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
          operationName "GET"
          spanKind CLIENT
          errored false
          attributes {
            SemanticAttributes.NET_TRANSPORT.key() "IP.TCP"
            SemanticAttributes.NET_PEER_IP.key() "127.0.0.1"
            SemanticAttributes.NET_PEER_PORT.key() port
            "db.connection_string" "redis://127.0.0.1:$port"
            "db.system" "redis"
            SemanticAttributes.DB_STATEMENT.key() "GET key<TESTKEY>"
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
          operationName "GET"
          spanKind CLIENT
          errored false
          attributes {
            SemanticAttributes.NET_TRANSPORT.key() "IP.TCP"
            SemanticAttributes.NET_PEER_IP.key() "127.0.0.1"
            SemanticAttributes.NET_PEER_PORT.key() port
            "db.connection_string" "redis://127.0.0.1:$port"
            "db.system" "redis"
            SemanticAttributes.DB_STATEMENT.key() "GET key<NON_EXISTENT_KEY>"
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
          operationName "RANDOMKEY"
          spanKind CLIENT
          errored false
          attributes {
            SemanticAttributes.NET_TRANSPORT.key() "IP.TCP"
            SemanticAttributes.NET_PEER_IP.key() "127.0.0.1"
            SemanticAttributes.NET_PEER_PORT.key() port
            "db.connection_string" "redis://127.0.0.1:$port"
            SemanticAttributes.DB_STATEMENT.key() "RANDOMKEY"
            "db.system" "redis"
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
          operationName "LPUSH"
          spanKind CLIENT
          errored false
          attributes {
            SemanticAttributes.NET_TRANSPORT.key() "IP.TCP"
            SemanticAttributes.NET_PEER_IP.key() "127.0.0.1"
            SemanticAttributes.NET_PEER_PORT.key() port
            "db.connection_string" "redis://127.0.0.1:$port"
            "db.system" "redis"
            SemanticAttributes.DB_STATEMENT.key() "LPUSH key<TESTLIST> value<TESTLIST ELEMENT>"
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
          operationName "HMSET"
          spanKind CLIENT
          errored false
          attributes {
            SemanticAttributes.NET_TRANSPORT.key() "IP.TCP"
            SemanticAttributes.NET_PEER_IP.key() "127.0.0.1"
            SemanticAttributes.NET_PEER_PORT.key() port
            "db.connection_string" "redis://127.0.0.1:$port"
            "db.system" "redis"
            SemanticAttributes.DB_STATEMENT.key() "HMSET key<user> key<firstname> value<John> key<lastname> value<Doe> key<age> value<53>"
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
          operationName "HGETALL"
          spanKind CLIENT
          errored false
          attributes {
            SemanticAttributes.NET_TRANSPORT.key() "IP.TCP"
            SemanticAttributes.NET_PEER_IP.key() "127.0.0.1"
            SemanticAttributes.NET_PEER_PORT.key() port
            "db.connection_string" "redis://127.0.0.1:$port"
            "db.system" "redis"
            SemanticAttributes.DB_STATEMENT.key() "HGETALL key<TESTHM>"
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
