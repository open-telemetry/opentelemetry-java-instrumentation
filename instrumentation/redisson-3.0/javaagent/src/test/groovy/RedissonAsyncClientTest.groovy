/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.redisson.Redisson
import org.redisson.api.RBucket
import org.redisson.api.RFuture
import org.redisson.api.RSet
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.redisson.config.SingleServerConfig
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class RedissonAsyncClientTest extends AgentInstrumentationSpecification {

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
    singleServerConfig.setTimeout(30_000)
    // disable connection ping if it exists
    singleServerConfig.metaClass.getMetaMethod("setPingConnectionInterval", int)?.invoke(singleServerConfig, 0)
    redisson = Redisson.create(config)
    clearExportedData()
  }

  def "test future set"() {
    when:
    RBucket<String> keyObject = redisson.getBucket("foo")
    RFuture future = keyObject.setAsync("bar")
    future.get(30, TimeUnit.SECONDS)

    then:
    assertTraces(1) {
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
    }
  }

  def "test future whenComplete"() {
    when:
    RSet<String> rSet = redisson.getSet("set1")
    RFuture<Boolean> result = runWithSpan("parent") {
      RFuture<Boolean> result = rSet.addAsync("s1")
      result.whenComplete({ res, throwable ->
        if (!Span.current().getSpanContext().isValid()) {
          new Exception("Callback should have a parent span.").printStackTrace()
        }
        runWithSpan("callback") {
        }
      })
      return result
    }

    then:
    result.get(30, TimeUnit.SECONDS)
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "SADD"
          kind CLIENT
          childOf(span(0))
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_STATEMENT" "SADD set1 ?"
            "$SemanticAttributes.DB_OPERATION" "SADD"
          }
        }
        span(2) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }
  }

}

