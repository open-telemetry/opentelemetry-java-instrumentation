/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.testcontainers.containers.GenericContainer
import redis.clients.jedis.GeoUnit
import redis.clients.jedis.Jedis
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT

class Jedis30ClientTest extends AgentInstrumentationSpecification {

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)

  @Shared
  int port

  @Shared
  Jedis jedis

  def setupSpec() {
    redisServer.start()
    port = redisServer.getMappedPort(6379)
    jedis = new Jedis("localhost", port)
  }

  def cleanupSpec() {
    redisServer.stop()
    jedis.close()
  }

  def setup() {
    jedis.flushAll()
    clearExportedData()
  }

  def "set command"() {
    when:
    jedis.set("foo", "bar")

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SET foo ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
    }
  }

  def "get command"() {
    when:
    jedis.set("foo", "bar")
    def value = jedis.get("foo")

    then:
    value == "bar"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SET foo ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "GET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "GET foo"
            "$SemanticAttributes.DB_OPERATION" "GET"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
    }
  }

  def "command with no arguments"() {
    when:
    jedis.set("foo", "bar")
    def value = jedis.randomKey()

    then:
    value == "foo"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SET foo ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "RANDOMKEY"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "RANDOMKEY"
            "$SemanticAttributes.DB_OPERATION" "RANDOMKEY"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
    }
  }

  def "restoreReplace method"() {
    when:
    jedis.set("foo1", "bar1")
    jedis.set("foo2", "bar2")
    def dump = jedis.dump("foo1")
    jedis.restoreReplace("foo2", 0, dump)
    def value = jedis.get("foo2")

    then:
    value == "bar1"

    assertTraces(5) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SET foo1 ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SET foo2 ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
      trace(2, 1) {
        span(0) {
          name "DUMP"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "DUMP foo1"
            "$SemanticAttributes.DB_OPERATION" "DUMP"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
      trace(3, 1) {
        span(0) {
          name "RESTORE"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "RESTORE replace foo2 ? ?"
            "$SemanticAttributes.DB_OPERATION" "RESTORE"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
      trace(4, 1) {
        span(0) {
          name "GET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "GET foo2"
            "$SemanticAttributes.DB_OPERATION" "GET"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
    }
  }

  def "georadiusReadonly method"() {
    when:
    jedis.georadiusReadonly("Sicily", 15.0, 37.0, 200.0, GeoUnit.KM)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "GEORADIUS_RO"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "GEORADIUS_RO Sicily 15.0 37.0 200.0 KM"
            "$SemanticAttributes.DB_OPERATION" "GEORADIUS_RO"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
    }
  }

  def "pubsubNumPat method"() {
    when:
    jedis.pubsubNumPat()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "PUBSUB"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "PUBSUB numpat"
            "$SemanticAttributes.DB_OPERATION" "PUBSUB"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
    }
  }

  def "getDB method"() {
    when:
    jedis.select(0)
    def db = jedis.getDB()

    then:
    db == 0

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SELECT"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SELECT 0"
            "$SemanticAttributes.DB_OPERATION" "SELECT"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
    }
  }

  def "salveOfNoOne method"() {
    when:
    jedis.slaveofNoOne()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SLAVEOF"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SLAVEOF no one"
            "$SemanticAttributes.DB_OPERATION" "SLAVEOF"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_TRANSPORT" SemanticAttributes.NetTransportValues.IP_TCP
          }
        }
      }
    }
  }
}
