/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
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
import org.redisson.config.SingleServerConfig
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static java.util.regex.Pattern.compile
import static java.util.regex.Pattern.quote

class RedissonClientTest extends AgentInstrumentationSpecification {

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)
  @Shared
  int port

  @Shared
  RedissonClient redisson
  @Shared
  String address

  def setupSpec() {
    redisServer.start()
    port = redisServer.getMappedPort(6379)
    address = "localhost:" + port
    if (Boolean.getBoolean("testLatestDeps")) {
      // Newer versions of redisson require scheme, older versions forbid it
      address = "redis://" + address
    }
  }

  def cleanupSpec() {
    redisson.shutdown()
    redisServer.stop()
  }

  def setup() {
    Config config = new Config()
    SingleServerConfig singleServerConfig = config.useSingleServer()
    singleServerConfig.setAddress(address)
    // disable connection ping if it exists
    singleServerConfig.metaClass.getMetaMethod("setPingConnectionInterval", int)?.invoke(singleServerConfig, 0)
    redisson = Redisson.create(config)
    clearExportedData()
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "SET foo ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "GET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "GET foo"
            "$SemanticAttributes.DB_OPERATION" "GET"
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
          name "DB Query"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "SET batch1 ?;SET batch2 ?"
            "$SemanticAttributes.DB_OPERATION" null
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "RPUSH list1 ?"
            "$SemanticAttributes.DB_OPERATION" "RPUSH"
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
          def script = "local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return v"

          name "EVAL"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "EVAL $script 1 map1 ? ?"
            "$SemanticAttributes.DB_OPERATION" "EVAL"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "HGET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "HGET map1 key1"
            "$SemanticAttributes.DB_OPERATION" "HGET"
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "SADD set1 ?"
            "$SemanticAttributes.DB_OPERATION" "SADD"
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "ZADD sort_set1 ? ? ? ? ? ?"
            "$SemanticAttributes.DB_OPERATION" "ZADD"
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
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "INCR AtomicLong"
            "$SemanticAttributes.DB_OPERATION" "INCR"
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
          // Use .* to match the actual script, since it changes between redisson versions
          // everything that does not change is quoted so that it's matched literally
          def lockScriptPattern = compile("^" + quote("EVAL ") + ".*" + quote(" 1 lock ? ?") + "\$")

          name "EVAL"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" { lockScriptPattern.matcher(it).matches() }
            "$SemanticAttributes.DB_OPERATION" "EVAL"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          def lockScriptPattern = compile("^" + quote("EVAL ") + ".*" + quote(" 2 lock ") + "\\S+" + quote(" ? ? ?") + "\$")

          name "EVAL"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" { lockScriptPattern.matcher(it).matches() }
            "$SemanticAttributes.DB_OPERATION" "EVAL"
          }
        }
      }
    }
  }
}

