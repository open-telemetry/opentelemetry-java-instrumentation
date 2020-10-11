/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import org.redisson.Redisson
import org.redisson.api.RAtomicLong
import org.redisson.api.RBatch
import org.redisson.api.RBucket
import org.redisson.api.RList
import org.redisson.api.RLock
import org.redisson.api.RMap
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RSet
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import redis.embedded.RedisServer
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT

class RedissonClientTest extends AgentTestRunner {

  @Shared
  int port = PortUtils.randomOpenPort()

  @Shared
  RedisServer redisServer = RedisServer.builder()
  // bind to localhost to avoid firewall popup
    .setting("bind 127.0.0.1")
  // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(port).build()
  @Shared
  RedissonClient redisson
  @Shared
  String address = "localhost:" + port

  def setupSpec() {
    if (Boolean.getBoolean("testLatestDeps")) {
      // Newer versions of redisson require scheme, older versions forbid it
      address = "redis://" + address
    }
    println "Using redis: $redisServer.args"
    redisServer.start()
  }

  def cleanupSpec() {
    redisson.shutdown()
    redisServer.stop()
  }

  def setup() {
    Config config = new Config()
    config.useSingleServer().setAddress(address)
    redisson = Redisson.create(config)
    TEST_WRITER.clear()
  }

  def "test string command"() {
    when:
    RBucket<String> keyObject = redisson.getBucket("foo")
    keyObject.set("bar")
    keyObject.get()
    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "GET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
          }
        }
      }
    }
  }

  def "test batch command"() {
    when:
    RBatch batch = redisson.createBatch()
    batch.getBucket("batch1").setAsync("v1")
    batch.getBucket("batch2").setAsync("v2")
    batch.execute()
    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET;SET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "SET;SET"
          }
        }
      }
    }
  }

  def "test list command"() {
    when:
    RList<String> strings = redisson.getList("list1")
    strings.add("a")
    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "RPUSH"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "RPUSH"
          }
        }
      }
    }
  }

  def "test hash command"() {
    when:
    RMap<String, String> rMap = redisson.getMap("map1")
    rMap.put("key1", "value1")
    rMap.get("key1")
    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "EVAL"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "EVAL"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "HGET"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "HGET"
          }
        }
      }
    }
  }

  def "test set command"() {
    when:
    RSet<String> rSet = redisson.getSet("set1")
    rSet.add("s1")
    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SADD"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "SADD"
          }
        }
      }
    }
  }

  def "test sorted set command"() {
    when:
    Map<String, Double> scores = new HashMap<>()
    scores.put("u1", 1.0d)
    scores.put("u2", 3.0d)
    scores.put("u3", 0.0d)
    RScoredSortedSet<String> sortSet = redisson.getScoredSortedSet("sort_set1")
    sortSet.addAll(scores)
    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "ZADD"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "ZADD"
          }
        }
      }
    }
  }

  def "test AtomicLong command"() {
    when:
    RAtomicLong atomicLong = redisson.getAtomicLong("AtomicLong")
    atomicLong.incrementAndGet()
    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "INCR"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "INCR"
          }
        }
      }
    }
  }

  def "test lock command"() {
    when:
    RLock lock = redisson.getLock("lock")
    lock.lock()
    lock.unlock()
    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "EVAL"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "EVAL"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "EVAL"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key()}" "redis"
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.DB_CONNECTION_STRING.key()}" "localhost:$port"
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${SemanticAttributes.DB_STATEMENT.key()}" "EVAL"
          }
        }
      }
    }
  }
}

