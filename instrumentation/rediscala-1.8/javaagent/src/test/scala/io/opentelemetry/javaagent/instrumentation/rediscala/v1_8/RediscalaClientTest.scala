/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rediscala

import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv
import io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS
import io.opentelemetry.semconv.DbAttributes.{DB_OPERATION_NAME, DB_SYSTEM_NAME}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.containers.GenericContainer
import redis.commands.TransactionBuilder
import redis.{RedisClient, RedisDispatcher}

import java.util.function.Consumer
import java.util.stream.Stream
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
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), "SET")
                )
            }
          }
        )
    })

    assertDurationMetric(
      testing,
      "io.opentelemetry.rediscala-1.8",
      DB_SYSTEM_NAME,
      DB_OPERATION_NAME
    )
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
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), "SET")
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
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), "GET")
                )
            }
          }
        )
    })
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource(Array("transactionScenarios"))
  def testTransaction(scenario: BatchScenario): Unit = {
    val result = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[_], Exception] {
        override def get(): Future[_] = {
          val transaction = redisClient.multi()
          scenario.commands(transaction)
          transaction.exec()
        }
      }
    )

    Await.result(result, Duration.apply("3 second"))

    assertTransactionSpan(scenario.operation, scenario.batchSize)
  }

  private def transactionScenarios(): Stream[BatchScenario] = {
    Stream.of(
      BatchScenario
        .builder("single")
        .commands(transaction => transaction.set("transaction-single", "value"))
        .operation("MULTI SET")
        .build(),
      BatchScenario
        .builder("twoSameOperation")
        .commands((transaction: TransactionBuilder) => {
          transaction.set("transaction-same-1", "value")
          transaction.set("transaction-same-2", "value")
        })
        .operation("MULTI SET")
        .batchSize(2)
        .build(),
      BatchScenario
        .builder("twoDifferentOperations")
        .commands((transaction: TransactionBuilder) => {
          transaction.set("transaction-different", "value")
          transaction.get[String]("transaction-different")
        })
        .operation("MULTI")
        .batchSize(2)
        .build()
    )
  }

  private def assertTransactionSpan(
      operation: String,
      batchSize: java.lang.Long
  ): Unit = {
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
                .hasName(operation)
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), operation),
                  equalTo(
                    DB_OPERATION_BATCH_SIZE,
                    if (emitStableDatabaseSemconv()) batchSize else null
                  )
                )
            }
          }
        )
    })
  }

  private class BatchScenario private (
      val name: String,
      val commands: TransactionBuilder => Unit,
      val operation: String,
      val batchSize: java.lang.Long
  ) {
    override def toString: String = name
  }

  private object BatchScenario {
    def builder(name: String): Builder = new Builder(name)

    class Builder private[BatchScenario] (name: String) {
      private var commands: TransactionBuilder => Unit = _
      private var operation: String = _
      private var batchSize: java.lang.Long = _

      def commands(commands: TransactionBuilder => Unit): Builder = {
        this.commands = commands
        this
      }

      def operation(operation: String): Builder = {
        this.operation = operation
        this
      }

      def batchSize(batchSize: Long): Builder = {
        this.batchSize = batchSize
        this
      }

      def build(): BatchScenario =
        new BatchScenario(name, commands, operation, batchSize)
    }
  }
}
