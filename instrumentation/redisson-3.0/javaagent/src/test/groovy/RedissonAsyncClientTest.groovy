/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.TimeUnit
import org.redisson.Redisson
import org.redisson.api.RBucket
import org.redisson.api.RFuture
import org.redisson.api.RList
import org.redisson.api.RSet
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.redisson.config.SingleServerConfig
import redis.embedded.RedisServer
import spock.lang.Shared

class RedissonAsyncClientTest extends AgentInstrumentationSpecification {

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
    SingleServerConfig singleServerConfig = config.useSingleServer()
    singleServerConfig.setAddress(address)
    // disable connection ping if it exists
    singleServerConfig.metaClass.getMetaMethod("setPingConnectionInterval", int)?.invoke(singleServerConfig, 0)
    redisson = Redisson.create(config)
    clearExportedData()
  }

  def "test future set"() {
    when:
    RBucket<String> keyObject = redisson.getBucket("foo")
    RFuture future = keyObject.setAsync("bar")
    future.get(3, TimeUnit.SECONDS)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.NET_PEER_IP.key" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME.key" "localhost"
            "$SemanticAttributes.DB_CONNECTION_STRING.key" "localhost:$port"
            "$SemanticAttributes.NET_PEER_PORT.key" port
            "$SemanticAttributes.DB_STATEMENT.key" "SET foo ?"
          }
        }
      }
    }
  }

  def "test future whenComplete"() {
    when:
    RSet<String> rSet = redisson.getSet("set1")
    RFuture<Boolean> result = rSet.addAsync("s1")
    result.whenComplete({ res, throwable ->
      RList<String> strings = redisson.getList("list1")
      strings.add("a")
    })

    then:
    result.get(3, TimeUnit.SECONDS)
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "SADD"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.NET_PEER_IP.key" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME.key" "localhost"
            "$SemanticAttributes.DB_CONNECTION_STRING.key" "localhost:$port"
            "$SemanticAttributes.NET_PEER_PORT.key" port
            "$SemanticAttributes.DB_STATEMENT.key" "SADD set1 ?"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "RPUSH"
          kind CLIENT
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" "redis"
            "$SemanticAttributes.NET_PEER_IP.key" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME.key" "localhost"
            "$SemanticAttributes.DB_CONNECTION_STRING.key" "localhost:$port"
            "$SemanticAttributes.NET_PEER_PORT.key" port
            "$SemanticAttributes.DB_STATEMENT.key" "RPUSH list1 ?"
          }
        }
      }
    }
  }

}

