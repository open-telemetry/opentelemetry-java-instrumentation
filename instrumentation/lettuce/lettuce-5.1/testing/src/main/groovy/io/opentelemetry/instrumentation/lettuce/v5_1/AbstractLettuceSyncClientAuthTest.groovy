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

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)

  abstract RedisClient createClient(String uri)

  @Shared
  String host
  @Shared
  int port
  @Shared
  String password
  @Shared
  String dbAddr
  @Shared
  String embeddedDbUri

  RedisClient redisClient

  def setupSpec() {
    password = "password"

    redisServer = redisServer
      .withCommand("redis-server", "--requirepass $password")
  }

  def setup() {
    redisServer.start()
    host = redisServer.getHost()
    port = redisServer.getMappedPort(6379)
    dbAddr = host + ":" + port + "/" + DB_INDEX
    embeddedDbUri = "redis://" + dbAddr

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
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" host
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" port
            "${SemanticAttributes.DB_SYSTEM.key}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key}" "AUTH ?"
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
