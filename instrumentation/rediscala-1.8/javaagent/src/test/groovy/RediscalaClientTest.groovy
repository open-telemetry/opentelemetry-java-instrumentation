/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes
import org.testcontainers.containers.GenericContainer
import redis.ByteStringDeserializerDefault
import redis.ByteStringSerializerLowPriority
import redis.RedisClient
import redis.RedisDispatcher
import scala.Option
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT

class RediscalaClientTest extends AgentInstrumentationSpecification {

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)

  @Shared
  int port

  @Shared
  def system

  @Shared
  RedisClient redisClient

  def setupSpec() {
    redisServer.start()
    String host = redisServer.getHost()
    port = redisServer.getMappedPort(6379)
    // latest has separate artifacts for akka an pekko, currently latestDepTestLibrary picks the
    // pekko one
    try {
      def clazz = Class.forName("akka.actor.ActorSystem")
      system = clazz.getMethod("create").invoke(null)
    } catch (ClassNotFoundException exception) {
      def clazz = Class.forName("org.apache.pekko.actor.ActorSystem")
      system = clazz.getMethod("create").invoke(null)
    }
    // latest RedisClient constructor takes username as argument
    if (RedisClient.metaClass.getMetaMethod("username") != null) {
      redisClient = new RedisClient(host,
        port,
        Option.apply(null),
        Option.apply(null),
        Option.apply(null),
        "RedisClient",
        Option.apply(null),
        system,
        new RedisDispatcher("rediscala.rediscala-client-worker-dispatcher"))
    } else {
      redisClient = new RedisClient(host,
        port,
        Option.apply(null),
        Option.apply(null),
        "RedisClient",
        Option.apply(null),
        system,
        new RedisDispatcher("rediscala.rediscala-client-worker-dispatcher"))
    }
  }

  def cleanupSpec() {
    redisServer.stop()
    system?.terminate()
  }

  def "set command"() {
    when:
    def value = redisClient.set("foo",
      "bar",
      Option.apply(null),
      Option.apply(null),
      false,
      false,
      new ByteStringSerializerLowPriority.String$())


    then:
    Await.result(value, Duration.apply("3 second")) == true
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "$DbIncubatingAttributes.DB_SYSTEM" "redis"
            "$DbIncubatingAttributes.DB_OPERATION" "SET"
          }
        }
      }
    }
  }

  def "get command"() {
    when:
    def (write, value) = runWithSpan("parent") {
      def w = redisClient.set("bar",
        "baz",
        Option.apply(null),
        Option.apply(null),
        false,
        false,
        new ByteStringSerializerLowPriority.String$())
      def v = redisClient.get("bar", new ByteStringDeserializerDefault.String$())
      return new Tuple(w, v)
    }

    then:
    Await.result(write, Duration.apply("3 second")) == true
    Await.result(value, Duration.apply("3 second")) == Option.apply("baz")
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
        }
        span(1) {
          name "SET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$DbIncubatingAttributes.DB_SYSTEM" "redis"
            "$DbIncubatingAttributes.DB_OPERATION" "SET"
          }
        }
        span(2) {
          name "GET"
          kind CLIENT
          childOf span(0)
          attributes {
            "$DbIncubatingAttributes.DB_SYSTEM" "redis"
            "$DbIncubatingAttributes.DB_OPERATION" "GET"
          }
        }
      }
    }
  }
}
