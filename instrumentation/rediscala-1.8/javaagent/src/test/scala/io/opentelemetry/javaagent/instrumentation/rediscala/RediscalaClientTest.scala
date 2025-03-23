/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rediscala

import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.REDIS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import redis.{RedisClient, RedisDispatcher}

import java.util.function.Consumer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RediscalaClientTest {

  @RegisterExtension val testing = AgentInstrumentationExtension.create

  var system: Object = null
  var redisServer: GenericContainer[_] = null
  var redisClient: RedisClient = null

  @BeforeAll
  def setUp(): Unit = {
    redisServer =
      new GenericContainer("redis:6.2.3-alpine").withExposedPorts(6379)
    redisServer.start()

    val host: String = redisServer.getHost
    val port: Integer = redisServer.getMappedPort(6379)

    try {
      val clazz = Class.forName("akka.actor.ActorSystem")
      system = clazz.getMethod("create").invoke(null)
    } catch {
      case _: ClassNotFoundException =>
        val clazz = Class.forName("org.apache.pekko.actor.ActorSystem")
        system = clazz.getMethod("create").invoke(null)
    }

    try {
      // latest RedisClient constructor takes username as argument
      classOf[RedisClient].getMethod("username")
      redisClient = classOf[RedisClient]
        .getConstructors()(0)
        .newInstance(
          host,
          port,
          Option.apply(null),
          Option.apply(null),
          Option.apply(null),
          "RedisClient",
          Option.apply(null),
          system,
          RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
        )
        .asInstanceOf[RedisClient]
    } catch {
      case _: Exception =>
        redisClient = classOf[RedisClient]
          .getConstructors()(0)
          .newInstance(
            host,
            port,
            Option.apply(null),
            Option.apply(null),
            "RedisClient",
            Option.apply(null),
            system,
            RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
          )
          .asInstanceOf[RedisClient]
    }
  }

  @AfterAll
  def tearDown(): Unit = {
    if (system != null) {
      system.getClass.getMethod("terminate").invoke(system)
    }
    redisServer.stop()
  }

  @Test def testSetCommand(): Unit = {
    val value = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[Boolean], Exception] {
        override def get(): Future[Boolean] = {
          redisClient.set("foo", "bar")
        }
      }
    )

    assertThat(Await.result(value, Duration.apply("3 second"))).isTrue
    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span.hasName("parent").hasNoParent
            }
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("SET")
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(SemconvStabilityUtil.maybeStable(DB_SYSTEM), REDIS),
                  equalTo(SemconvStabilityUtil.maybeStable(DB_OPERATION), "SET")
                )
            }
          }
        )
    })
  }

  @Test def testGetCommand(): Unit = {
    val (write, value) = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[
        (Future[Boolean], Future[Option[String]]),
        Exception
      ] {
        override def get(): (Future[Boolean], Future[Option[String]]) = {
          val write = redisClient.set("bar", "baz")
          val value = redisClient.get[String]("bar")
          (write, value)
        }
      }
    )

    assertThat(Await.result(write, Duration.apply("3 second"))).isTrue
    assertThat(
      Await
        .result(value, Duration.apply("3 second"))
        .get
    ).isEqualTo("baz")

    testing.waitAndAssertTraces(new Consumer[TraceAssert] {
      override def accept(trace: TraceAssert): Unit =
        trace.hasSpansSatisfyingExactly(
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span.hasName("parent").hasNoParent
            }
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("SET")
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(SemconvStabilityUtil.maybeStable(DB_SYSTEM), REDIS),
                  equalTo(SemconvStabilityUtil.maybeStable(DB_OPERATION), "SET")
                )
            }
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName("GET")
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(SemconvStabilityUtil.maybeStable(DB_SYSTEM), REDIS),
                  equalTo(SemconvStabilityUtil.maybeStable(DB_OPERATION), "GET")
                )
            }
          }
        )
    })
  }
}
