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
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.{
  assertThat => assertThatSpan,
  equalTo
}
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.testing.assertj.{SpanDataAssert, TraceAssert}
import io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS
import io.opentelemetry.semconv.DbAttributes.{
  DB_NAMESPACE,
  DB_OPERATION_NAME,
  DB_SYSTEM_NAME
}
import io.opentelemetry.semconv.ServerAttributes.{SERVER_ADDRESS, SERVER_PORT}
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.awaitility.core.ThrowingRunnable
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}
import org.testcontainers.containers.GenericContainer
import redis.commands.TransactionBuilder
import redis.{
  RedisClient,
  RedisClientPool,
  RedisDispatcher,
  RedisServer,
  SentinelMonitoredRedisClientMasterSlaves
}

import java.lang.{Long => JLong}
import java.net.InetAddress
import java.util.function.{Consumer, Predicate}
import java.util.stream.Stream
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RediscalaClientTest {

  @RegisterExtension val testing = AgentInstrumentationExtension.create

  private val defaultDbIndex = 0
  private val nonDefaultDbIndex = 1

  var system: Object = null
  var redisServer: GenericContainer[_] = null
  var sentinelServer: GenericContainer[_] = null
  var redisClient: RedisClient = null
  var nonDefaultDbClient: RedisClient = null
  var host: String = null
  var port: JLong = null
  var sentinelHost: String = null
  var sentinelPort: JLong = null

  @BeforeAll
  def setUp(): Unit = {
    redisServer =
      new GenericContainer("redis:6.2.3-alpine").withExposedPorts(6379)
    redisServer.start()

    host = redisServer.getHost
    port = redisServer.getMappedPort(6379).longValue()

    if (emitStableDatabaseSemconv()) {
      sentinelServer = new GenericContainer("redis:6.2.3-alpine")
      sentinelServer.withExposedPorts(26379)
      sentinelServer.withExtraHost(host, "127.0.0.1")
      sentinelServer.withCommand(
        "sh",
        "-c",
        s"redis-server --port $port --daemonize yes && printf 'port 26379\\nsentinel resolve-hostnames yes\\nsentinel announce-hostnames yes\\nsentinel monitor mymaster $host $port 1\\n' > /tmp/sentinel.conf && exec redis-server /tmp/sentinel.conf --sentinel"
      )
      sentinelServer.start()
      sentinelHost = sentinelServer.getHost
      sentinelPort = sentinelServer.getMappedPort(26379).longValue()
    }

    try {
      val clazz = Class.forName("akka.actor.ActorSystem")
      system = clazz.getMethod("create").invoke(null)
    } catch {
      case _: ClassNotFoundException =>
        val clazz = Class.forName("org.apache.pekko.actor.ActorSystem")
        system = clazz.getMethod("create").invoke(null)
    }

    redisClient = createClient(None)
    nonDefaultDbClient = createClient(Some(nonDefaultDbIndex))
  }

  private def createClient(db: Option[Int]): RedisClient =
    try {
      // latest RedisClient constructor takes username as argument
      classOf[RedisClient].getMethod("username")
      classOf[RedisClient]
        .getConstructors()(0)
        .newInstance(
          host,
          Integer.valueOf(port.intValue()),
          Option.apply(null),
          Option.apply(null),
          db,
          "RedisClient",
          Option.apply(null),
          system,
          RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
        )
        .asInstanceOf[RedisClient]
    } catch {
      case _: Exception =>
        classOf[RedisClient]
          .getConstructors()(0)
          .newInstance(
            host,
            Integer.valueOf(port.intValue()),
            Option.apply(null),
            db,
            "RedisClient",
            Option.apply(null),
            system,
            RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
          )
          .asInstanceOf[RedisClient]
    }

  @AfterAll
  def tearDown(): Unit = {
    if (system != null) {
      system.getClass.getMethod("terminate").invoke(system)
    }
    if (sentinelServer != null) {
      sentinelServer.stop()
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
                .hasName(spanName("SET"))
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), "SET"),
                  equalTo(DB_NAMESPACE, namespace(defaultDbIndex)),
                  equalTo(SERVER_ADDRESS, host),
                  equalTo(SERVER_PORT, port)
                )
            }
          }
        )
    })

    assertDurationMetric(
      testing,
      "io.opentelemetry.rediscala-1.8",
      DB_SYSTEM_NAME,
      DB_OPERATION_NAME,
      DB_NAMESPACE,
      SERVER_ADDRESS,
      SERVER_PORT
    )
  }

  @Test def testReconnectRefreshesServerTarget(): Unit = {
    val client = createClient(None)
    try {
      assertThat(serverTarget(client)._1).isEqualTo(host)
      assertThat(serverTarget(client)._2)
        .isEqualTo(Integer.valueOf(port.intValue()))

      client.reconnect("127.0.0.2", 16379)

      assertThat(serverTarget(client)._1).isEqualTo("127.0.0.2")
      assertThat(serverTarget(client)._2)
        .isEqualTo(Integer.valueOf(16379))
    } finally {
      client.stop()
    }
  }

  @Test def testTransactionRefreshesServerTarget(): Unit = {
    val client = createClient(None)
    try {
      val transaction = client.multi()
      transaction.set("transaction-refresh", "value")
      val reconnectHost = alternateHost(host)
      client.reconnect(reconnectHost, port.intValue())

      val result = testing.runWithSpan(
        "parent",
        new ThrowingSupplier[Future[_], Exception] {
          override def get(): Future[_] = transaction.exec()
        }
      )

      Await.result(result, Duration("3 second"))
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
                  .hasName(
                    if (emitStableDatabaseSemconv())
                      s"MULTI SET $reconnectHost:$port"
                    else "MULTI SET"
                  )
                  .hasKind(CLIENT)
                  .hasParent(trace.getSpan(0))
                  .hasAttributesSatisfyingExactly(
                    equalTo(maybeStable(DB_SYSTEM), REDIS),
                    equalTo(maybeStable(DB_OPERATION), "MULTI SET"),
                    equalTo(DB_NAMESPACE, namespace(defaultDbIndex)),
                    equalTo(
                      SERVER_ADDRESS,
                      if (emitStableDatabaseSemconv()) reconnectHost else host
                    ),
                    equalTo(SERVER_PORT, port)
                  )
              }
            }
          )
      })
    } finally {
      client.stop()
    }
  }

  @Test def testImmutablePoolCommandUsesConfiguredTarget(): Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    val hosts = Seq(host, alternateHost(host)).sorted
    val pool = classOf[RedisClientPool].getConstructors
      .find(_.getParameterCount == 4)
      .get
      .newInstance(
        hosts.map(RedisServer(_, port.intValue())),
        "RedisClientPool",
        system,
        RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )
      .asInstanceOf[RedisClientPool]
    try {
      val result = pool.set("immutable-pool-target", "value")
      Await.result(result, Duration("3 second"))
      assertConfiguredTargetSpan(
        hosts.map(serverHost => s"$serverHost:$port").mkString(",")
      )
    } finally {
      pool.stop()
    }
  }

  @Test def testSentinelMasterSlavesCommandUsesConfiguredTarget(): Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    val sentinelHosts =
      Seq(sentinelHost, alternateHost(sentinelHost)).sorted
    val client =
      classOf[SentinelMonitoredRedisClientMasterSlaves].getConstructors
        .find(_.getParameterCount == 4)
        .get
        .newInstance(
          sentinelHosts.map((_, sentinelPort.intValue())),
          "mymaster",
          system,
          RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
        )
        .asInstanceOf[SentinelMonitoredRedisClientMasterSlaves]
    try {
      val result = client.set("sentinel-target", "value")
      Await.result(result, Duration("10 second"))
      assertConfiguredTargetSpan(
        sentinelHosts
          .map(serverHost => s"$serverHost:$sentinelPort")
          .mkString(",") + "/mymaster"
      )
    } finally {
      client.stop()
    }
  }

  @Test def testMutablePoolRefreshesServerTarget(): Unit = {
    val first = RedisServer("127.0.0.2", 7001)
    val second = RedisServer("127.0.0.1", 7000)
    val poolClass = Class.forName("redis.RedisClientMutablePool")
    val constructor =
      poolClass.getConstructors.find(_.getParameterCount == 4).get
    val pool = constructor
      .newInstance(
        Seq(first),
        "RedisClientMutablePool",
        system,
        RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )
      .asInstanceOf[Object]
    try {
      assertThat(serverTarget(pool)._1).isEqualTo("127.0.0.2")
      assertThat(serverTarget(pool)._2)
        .isEqualTo(Integer.valueOf(7001))

      poolClass
        .getMethod("addServer", classOf[RedisServer])
        .invoke(pool, second)

      assertThat(serverTarget(pool)._1)
        .isEqualTo("127.0.0.1:7000,127.0.0.2:7001")
      assertThat(serverTarget(pool)._2).isNull()

      poolClass
        .getMethod("removeServer", classOf[RedisServer])
        .invoke(pool, first)

      assertThat(serverTarget(pool)._1).isEqualTo("127.0.0.1")
      assertThat(serverTarget(pool)._2)
        .isEqualTo(Integer.valueOf(7000))
    } finally {
      poolClass.getMethod("stop").invoke(pool)
    }
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
                .hasName(spanName("SET"))
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), "SET"),
                  equalTo(DB_NAMESPACE, namespace(defaultDbIndex)),
                  equalTo(SERVER_ADDRESS, host),
                  equalTo(SERVER_PORT, port)
                )
            }
          },
          new Consumer[SpanDataAssert] {
            override def accept(span: SpanDataAssert): Unit = {
              span
                .hasName(spanName("GET"))
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), "GET"),
                  equalTo(DB_NAMESPACE, namespace(defaultDbIndex)),
                  equalTo(SERVER_ADDRESS, host),
                  equalTo(SERVER_PORT, port)
                )
            }
          }
        )
    })
  }

  @Test def testContainerCommand(): Unit = {
    val value = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[_], Exception] {
        override def get(): Future[_] = redisClient.configGet("maxmemory")
      }
    )

    Await.result(value, Duration.apply("3 second"))

    // CONFIG GET is a container command, so the stable operation name is only the container token
    assertCommandSpan(
      if (emitStableDatabaseSemconv()) "CONFIG" else "CONFIGGET"
    )
  }

  @Test def testSingleTokenCommand(): Unit = {
    val value = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[_], Exception] {
        override def get(): Future[_] =
          redisClient.publish("channel", "message")
      }
    )

    Await.result(value, Duration.apply("3 second"))

    // PUBLISH is a single command, so it is reported unchanged in both modes
    assertCommandSpan("PUBLISH")
  }

  @Test def testCommandWithOption(): Unit = {
    val value = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[_], Exception] {
        override def get(): Future[_] =
          redisClient.zrangeWithscores[String]("sorted-set", 0, -1)
      }
    )

    Await.result(value, Duration.apply("3 second"))

    // ZrangeWithscores sends ZRANGE with the WITHSCORES option
    assertCommandSpan(
      if (emitStableDatabaseSemconv()) "ZRANGE" else "ZRANGEWITHSCORES"
    )
  }

  @Test def testCommandWithoutArguments(): Unit = {
    val value = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[_], Exception] {
        override def get(): Future[_] = redisClient.ping()
      }
    )

    Await.result(value, Duration.apply("3 second"))

    // commands without arguments are scala objects, whose class name ends with $
    assertCommandSpan(if (emitStableDatabaseSemconv()) "PING" else "PING$")
  }

  private def assertCommandSpan(operationName: String): Unit =
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
                .hasName(spanName(operationName))
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), operationName),
                  equalTo(DB_NAMESPACE, namespace(defaultDbIndex)),
                  equalTo(SERVER_ADDRESS, host),
                  equalTo(SERVER_PORT, port)
                )
            }
          }
        )
    })

  @ParameterizedTest
  @MethodSource(Array("transactionScenarios"))
  def testTransaction(scenario: BatchScenario): Unit = {
    val result = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[_], Exception] {
        override def get(): Future[_] = {
          val transaction = redisClient.multi()
          scenario.commands.foreach(_(transaction))
          transaction.exec()
        }
      }
    )

    Await.result(result, Duration("3 second"))

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
                .hasName(spanName(scenario.operationName))
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), scenario.operationName),
                  equalTo(DB_NAMESPACE, namespace(defaultDbIndex)),
                  equalTo(SERVER_ADDRESS, host),
                  equalTo(SERVER_PORT, port),
                  equalTo(
                    DB_OPERATION_BATCH_SIZE,
                    if (emitStableDatabaseSemconv()) scenario.batchSize
                    else null
                  )
                )
            }
          }
        )
    })
  }

  private def spanName(operation: String): String =
    if (emitStableDatabaseSemconv()) s"$operation $host:$port" else operation

  @Test def testNonDefaultDatabaseIndex(): Unit = {
    val value = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[Boolean], Exception] {
        override def get(): Future[Boolean] =
          nonDefaultDbClient.set("non-default-db", "value")
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
                // the database index is deliberately not part of the span name
                .hasName(spanName("SET"))
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), "SET"),
                  equalTo(DB_NAMESPACE, namespace(nonDefaultDbIndex)),
                  equalTo(SERVER_ADDRESS, host),
                  equalTo(SERVER_PORT, port)
                )
            }
          }
        )
    })

    assertDurationMetric(
      testing,
      "io.opentelemetry.rediscala-1.8",
      DB_SYSTEM_NAME,
      DB_OPERATION_NAME,
      DB_NAMESPACE,
      SERVER_ADDRESS,
      SERVER_PORT
    )
  }

  @Test def testNonDefaultDatabaseIndexTransaction(): Unit = {
    val result = testing.runWithSpan(
      "parent",
      new ThrowingSupplier[Future[_], Exception] {
        override def get(): Future[_] = {
          val transaction = nonDefaultDbClient.multi()
          transaction.set("non-default-db-transaction-1", "value")
          transaction.set("non-default-db-transaction-2", "value")
          transaction.exec()
        }
      }
    )

    Await.result(result, Duration("3 second"))
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
                // the database index is deliberately not part of the span name
                .hasName(spanName("MULTI SET"))
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), "MULTI SET"),
                  equalTo(DB_NAMESPACE, namespace(nonDefaultDbIndex)),
                  equalTo(SERVER_ADDRESS, host),
                  equalTo(SERVER_PORT, port),
                  equalTo(
                    DB_OPERATION_BATCH_SIZE,
                    if (emitStableDatabaseSemconv()) JLong.valueOf(2) else null
                  )
                )
            }
          }
        )
    })

    assertDurationMetric(
      testing,
      "io.opentelemetry.rediscala-1.8",
      DB_SYSTEM_NAME,
      DB_OPERATION_NAME,
      DB_NAMESPACE,
      SERVER_ADDRESS,
      SERVER_PORT
    )
  }

  private def namespace(databaseIndex: Int): String =
    if (emitStableDatabaseSemconv()) databaseIndex.toString else null

  private def alternateHost(serverHost: String): String = {
    val resolvedHost = InetAddress.getByName(serverHost).getHostAddress
    if (resolvedHost == serverHost) "localhost" else resolvedHost
  }

  private def assertConfiguredTargetSpan(serverAddress: String): Unit =
    await().untilAsserted(new ThrowingRunnable {
      override def run(): Unit = {
        val span = testing
          .spans()
          .stream()
          .filter(new Predicate[SpanData] {
            override def test(span: SpanData): Boolean =
              span.getName == s"SET $serverAddress"
          })
          .findFirst()
          .orElse(null)
        assertThat(span).isNotNull
        assertThatSpan(span)
          .hasName(s"SET $serverAddress")
          .hasKind(CLIENT)
          .hasAttributesSatisfyingExactly(
            equalTo(maybeStable(DB_SYSTEM), REDIS),
            equalTo(maybeStable(DB_OPERATION), "SET"),
            equalTo(SERVER_ADDRESS, serverAddress),
            equalTo(SERVER_PORT, null)
          )
      }
    })

  private def serverTarget(client: Object): (String, Integer) = {
    val helperClass = Class.forName(
      "io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaServerTargets",
      true,
      client.getClass.getClassLoader
    )
    val target =
      helperClass.getMethod("get", classOf[Object]).invoke(null, client)
    val address =
      target.getClass
        .getMethod("getAddress")
        .invoke(target)
        .asInstanceOf[String]
    val port =
      target.getClass.getMethod("getPort").invoke(target).asInstanceOf[Integer]
    (address, port)
  }

  private def transactionScenarios(): Stream[Arguments] =
    Stream.of(
      Arguments.argumentSet(
        "empty",
        BatchScenario(operationName = "MULTI", batchSize = 0L)
      ),
      Arguments.argumentSet(
        "single",
        BatchScenario(
          commands = Seq(_.set("transaction-single", "value")),
          operationName = "MULTI SET"
        )
      ),
      Arguments.argumentSet(
        "twoSameOperation",
        BatchScenario(
          commands = Seq(
            _.set("transaction-same-1", "value"),
            _.set("transaction-same-2", "value")
          ),
          operationName = "MULTI SET",
          batchSize = 2L
        )
      ),
      Arguments.argumentSet(
        "twoSameStableOperation",
        BatchScenario(
          commands = Seq(
            _.zrange[String]("transaction-same-stable", 0, -1),
            _.zrangeWithscores[String]("transaction-same-stable", 0, -1)
          ),
          // Zrange and ZrangeWithscores both send ZRANGE, so they group together only when the
          // stable operation name is used
          operationName =
            if (emitStableDatabaseSemconv()) "MULTI ZRANGE" else "MULTI",
          batchSize = 2L
        )
      ),
      Arguments.argumentSet(
        "twoDifferentOperations",
        BatchScenario(
          commands = Seq(
            _.set("transaction-different", "value"),
            _.get[String]("transaction-different")
          ),
          operationName = "MULTI",
          batchSize = 2L
        )
      )
    )

  private case class BatchScenario(
      commands: Seq[TransactionBuilder => Unit] = Seq.empty,
      operationName: String = null,
      batchSize: JLong = null
  )
}
