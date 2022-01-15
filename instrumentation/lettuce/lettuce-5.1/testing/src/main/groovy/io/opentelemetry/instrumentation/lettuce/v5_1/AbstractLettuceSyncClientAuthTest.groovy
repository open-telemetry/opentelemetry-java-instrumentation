/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1

import io.lettuce.core.RedisClient
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

abstract class AbstractLettuceSyncClientAuthTest extends InstrumentationSpecification {
  public static final int DB_INDEX = 0
  public static final String loopback = "127.0.0.1"

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)

  abstract RedisClient createClient(String uri)

  @Shared
  String expectedHostAttributeValue
  @Shared
  int port
  @Shared
  String password

  RedisClient redisClient

  def setupSpec() {
    password = "password"

    redisServer = redisServer
      .withCommand("redis-server", "--requirepass $password")
  }

  def setup() {
    redisServer.start()

    port = redisServer.getMappedPort(6379)
    String host = redisServer.getHost()
    String dbAddr = host + ":" + port + "/" + DB_INDEX
    String embeddedDbUri = "redis://" + dbAddr
    expectedHostAttributeValue = host == loopback ? null : host

    redisClient = createClient(embeddedDbUri)
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS)
  }

  def cleanup() {
    redisClient.shutdown()
    redisServer.stop()
  }

  def "auth command"() {
    setup:
    def res = redisClient.connect().sync().auth(password)

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "AUTH"
          kind CLIENT
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" expectedHostAttributeValue
            "$SemanticAttributes.NET_PEER_IP" loopback
            "$SemanticAttributes.NET_PEER_PORT" port
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "AUTH ?"
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
}
