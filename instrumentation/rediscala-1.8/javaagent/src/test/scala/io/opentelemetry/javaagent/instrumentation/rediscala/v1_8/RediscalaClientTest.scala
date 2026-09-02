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
import io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps
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
import io.opentelemetry.semconv.NetworkAttributes.{
  NETWORK_PEER_ADDRESS,
  NETWORK_PEER_PORT
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
  RedisClientMasterSlaves,
  RedisClientMutablePool,
  RedisClientPool,
  RedisCluster,
  RedisDispatcher,
  RedisServer,
  SentinelMonitoredRedisClient,
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
                  equalTo(NETWORK_PEER_ADDRESS, stablePeerAddress(host)),
                  equalTo(NETWORK_PEER_PORT, stablePeerPort(port)),
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
      NETWORK_PEER_ADDRESS,
      NETWORK_PEER_PORT,
      SERVER_ADDRESS,
      SERVER_PORT
    )
  }

  @Test def testReconnectRefreshesServerTarget(): Unit = {
    val client = createClient(None)
    try {
      val reconnectHost = alternateHost(host)
      client.reconnect(reconnectHost, port.intValue())

      val result = testing.runWithSpan(
        "parent",
        new ThrowingSupplier[Future[_], Exception] {
          override def get(): Future[_] =
            client.set("reconnect-refresh", "value")
        }
      )

      Await.result(result, Duration("3 second"))
      assertCommandSpan("SET", reconnectHost, port)
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
                      NETWORK_PEER_ADDRESS,
                      stablePeerAddress(reconnectHost)
                    ),
                    equalTo(NETWORK_PEER_PORT, stablePeerPort(port)),
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
    val hosts = Seq(alternateHost(host), host, alternateHost(host))
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
        hosts.sorted.map(serverHost => s"$serverHost:$port").mkString(",")
      )
    } finally {
      pool.stop()
    }
  }

  @Test def testClusterCommandUsesConfiguredTarget(): Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    assumeTrue(testLatestDeps())
    val clusterServer: GenericContainer[_] =
      new GenericContainer("redis:7.2-alpine")
    clusterServer.withExposedPorts(6379)
    clusterServer.withCommand(
      "redis-server",
      "--cluster-enabled",
      "yes",
      "--cluster-config-file",
      "nodes.conf"
    )
    clusterServer.start()
    try {
      val clusterHost = clusterServer.getHost
      val clusterAddress = InetAddress.getByName(clusterHost).getHostAddress
      val clusterPort = clusterServer.getMappedPort(6379)
      val setup = clusterServer.execInContainer(
        "sh",
        "-c",
        s"redis-cli CONFIG SET cluster-announce-ip $clusterAddress && redis-cli CONFIG SET cluster-announce-port $clusterPort && redis-cli CONFIG SET cluster-allow-reads-when-down yes && redis-cli CLUSTER ADDSLOTS $$(seq 0 16383)"
      )
      assertThat(setup.getExitCode).isZero
      val hosts = Seq(alternateHost(clusterHost), clusterHost)
      val cluster = classOf[RedisCluster].getConstructors
        .find(_.getParameterCount == 4)
        .get
        .newInstance(
          hosts.map(RedisServer(_, clusterPort)),
          "RedisCluster",
          system,
          RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
        )
        .asInstanceOf[RedisCluster]
      try {
        val result = cluster.get[String]("cluster-target")
        assertThat(Await.result(result, Duration("3 second")).isEmpty).isTrue
        assertConfiguredTargetSpan(
          hosts.sorted
            .map(serverHost => s"$serverHost:$clusterPort")
            .mkString(","),
          operationName = "GET"
        )
      } finally {
        cluster.stop()
      }
    } finally {
      clusterServer.stop()
    }
  }

  @Test def testMasterSlavesCommandUsesConfiguredTarget(): Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    val master = RedisServer(host, port.intValue())
    val slaves = Seq(
      RedisServer(host, port.intValue()),
      RedisServer(alternateHost(host), port.intValue())
    )
    val client = classOf[RedisClientMasterSlaves].getConstructors
      .find(_.getParameterCount == 4)
      .get
      .newInstance(
        master,
        slaves,
        system,
        RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )
      .asInstanceOf[RedisClientMasterSlaves]
    try {
      val result = client.set("master-slaves-target", "value")
      Await.result(result, Duration("3 second"))
      assertConfiguredTargetSpan(
        (s"${master.host}:${master.port}" +:
          slaves.map(server => s"${server.host}:${server.port}").sorted)
          .mkString(","),
        networkPeerAddress = host,
        networkPeerPort = port,
        databaseIndex = defaultDbIndex.toString
      )
    } finally {
      client.masterClient.stop()
      client.slavesClients.stop()
    }
  }

  @Test def testMasterSlavesTransactionUsesConfiguredTarget(): Unit = {
    val master = RedisServer(host, port.intValue())
    val slaves = Seq(
      RedisServer(host, port.intValue()),
      RedisServer(alternateHost(host), port.intValue())
    )
    val client = classOf[RedisClientMasterSlaves].getConstructors
      .find(_.getParameterCount == 4)
      .get
      .newInstance(
        master,
        slaves,
        system,
        RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )
      .asInstanceOf[RedisClientMasterSlaves]
    try {
      val transaction = client.multi()
      transaction.set("master-slaves-transaction-target", "value")
      Await.result(transaction.exec(), Duration("3 second"))
      assertConfiguredTargetSpan(
        if (emitStableDatabaseSemconv())
          (s"${master.host}:${master.port}" +:
            slaves.map(server => s"${server.host}:${server.port}").sorted)
            .mkString(",")
        else null,
        operationName = "MULTI SET",
        networkPeerAddress = host,
        networkPeerPort = port,
        databaseIndex = namespace(defaultDbIndex)
      )
    } finally {
      client.masterClient.stop()
      client.slavesClients.stop()
    }
  }

  @Test def testMasterSlavesReplicaCommandOmitsNetworkPeer(): Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    val master = RedisServer(host, port.intValue())
    val slaves = Seq(
      RedisServer(host, port.intValue()),
      RedisServer(alternateHost(host), port.intValue())
    )
    val client = classOf[RedisClientMasterSlaves].getConstructors
      .find(_.getParameterCount == 4)
      .get
      .newInstance(
        master,
        slaves,
        system,
        RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )
      .asInstanceOf[RedisClientMasterSlaves]
    try {
      Await.result(
        client.get[String]("master-slaves-replica-target"),
        Duration("3 second")
      )
      assertConfiguredTargetSpan(
        (s"${master.host}:${master.port}" +:
          slaves.map(server => s"${server.host}:${server.port}").sorted)
          .mkString(","),
        operationName = "GET"
      )
    } finally {
      client.masterClient.stop()
      client.slavesClients.stop()
    }
  }

  @Test def testSentinelMasterSlavesTransactionSeparatesConfiguredTargetFromNetworkPeer()
      : Unit = {
    val sentinelHosts = Seq(alternateHost(sentinelHost), sentinelHost)
    val client = createSentinelMasterSlavesClient(sentinelHosts)
    try {
      val transaction = client.multi()
      transaction.set("sentinel-master-slaves-transaction-peer", "value")
      Await.result(transaction.exec(), Duration("10 second"))
      assertConfiguredTargetSpan(
        if (emitStableDatabaseSemconv()) sentinelTarget(sentinelHosts)
        else null,
        operationName = "MULTI SET",
        networkPeerAddress = host,
        networkPeerPort = port,
        databaseIndex = namespace(defaultDbIndex)
      )
    } finally {
      client.stop()
    }
  }

  @Test def testSentinelMasterSlavesReplicaCommandOmitsNetworkPeer(): Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    val sentinelHosts = Seq(alternateHost(sentinelHost), sentinelHost)
    val client = createSentinelMasterSlavesClient(sentinelHosts)
    try {
      Await.result(
        client.get[String]("sentinel-master-slaves-replica-target"),
        Duration("10 second")
      )
      assertConfiguredTargetSpan(
        sentinelTarget(sentinelHosts),
        operationName = "GET"
      )
    } finally {
      client.stop()
    }
  }

  @Test def testSentinelMasterSlavesCommandUsesConfiguredTarget(): Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    val sentinelHosts = Seq(alternateHost(sentinelHost), sentinelHost)
    val client = createSentinelMasterSlavesClient(sentinelHosts)
    try {
      val result = client.set("sentinel-target", "value")
      Await.result(result, Duration("10 second"))
      assertConfiguredTargetSpan(
        sentinelTarget(sentinelHosts),
        networkPeerAddress = host,
        networkPeerPort = port,
        databaseIndex = defaultDbIndex.toString
      )
    } finally {
      client.stop()
    }
  }

  @Test def testSentinelCommandSeparatesConfiguredTargetFromNetworkPeer()
      : Unit = {
    val sentinelHosts = Seq(alternateHost(sentinelHost), sentinelHost)
    val client = createSentinelClient(sentinelHosts)
    try {
      val result = client.set("sentinel-peer", "value")
      Await.result(result, Duration("10 second"))
      assertConfiguredTargetSpan(
        if (emitStableDatabaseSemconv()) sentinelTarget(sentinelHosts)
        else null,
        networkPeerAddress = host,
        networkPeerPort = port,
        databaseIndex =
          if (emitStableDatabaseSemconv()) defaultDbIndex.toString else null
      )
    } finally {
      client.stop()
    }
  }

  @Test def testSentinelTransactionSeparatesConfiguredTargetFromNetworkPeer()
      : Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    val sentinelHosts = Seq(alternateHost(sentinelHost), sentinelHost)
    val client = createSentinelClient(sentinelHosts)
    try {
      val transaction = client.multi()
      transaction.set("sentinel-transaction-peer", "value")
      Await.result(transaction.exec(), Duration("10 second"))
      assertConfiguredTargetSpan(
        sentinelTarget(sentinelHosts),
        operationName = "MULTI SET",
        networkPeerAddress = host,
        networkPeerPort = port,
        databaseIndex = defaultDbIndex.toString
      )
    } finally {
      client.stop()
    }
  }

  @Test def testMutablePoolRefreshesServerTarget(): Unit = {
    assumeTrue(emitStableDatabaseSemconv())
    val first = RedisServer(host, port.intValue())
    val second = RedisServer(alternateHost(host), port.intValue())
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
      .asInstanceOf[RedisClientMutablePool]
    try {
      Await.result(
        pool.set("mutable-pool-initial-target", "value"),
        Duration("3 second")
      )
      assertConfiguredTargetSpan(first.host, JLong.valueOf(first.port))

      pool.addServer(second)

      Await.result(
        pool.set("mutable-pool-multiple-targets", "value"),
        Duration("3 second")
      )
      assertConfiguredTargetSpan(
        Seq(first, second)
          .map(server => s"${server.host}:${server.port}")
          .sorted
          .mkString(",")
      )

      pool.removeServer(first)

      Await.result(
        pool.set("mutable-pool-final-target", "value"),
        Duration("3 second")
      )
      assertConfiguredTargetSpan(second.host, JLong.valueOf(second.port))
    } finally {
      pool.stop()
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
                  equalTo(NETWORK_PEER_ADDRESS, stablePeerAddress(host)),
                  equalTo(NETWORK_PEER_PORT, stablePeerPort(port)),
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
                  equalTo(NETWORK_PEER_ADDRESS, stablePeerAddress(host)),
                  equalTo(NETWORK_PEER_PORT, stablePeerPort(port)),
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
    assertCommandSpan(operationName, host, port)

  private def assertCommandSpan(
      operationName: String,
      serverAddress: String,
      serverPort: JLong
  ): Unit =
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
                    s"$operationName $serverAddress:$serverPort"
                  else operationName
                )
                .hasKind(CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), operationName),
                  equalTo(DB_NAMESPACE, namespace(defaultDbIndex)),
                  equalTo(
                    NETWORK_PEER_ADDRESS,
                    stablePeerAddress(serverAddress)
                  ),
                  equalTo(NETWORK_PEER_PORT, stablePeerPort(serverPort)),
                  equalTo(SERVER_ADDRESS, serverAddress),
                  equalTo(SERVER_PORT, serverPort)
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
                  equalTo(NETWORK_PEER_ADDRESS, stablePeerAddress(host)),
                  equalTo(NETWORK_PEER_PORT, stablePeerPort(port)),
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
                  equalTo(NETWORK_PEER_ADDRESS, stablePeerAddress(host)),
                  equalTo(NETWORK_PEER_PORT, stablePeerPort(port)),
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
      NETWORK_PEER_ADDRESS,
      NETWORK_PEER_PORT,
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
                  equalTo(NETWORK_PEER_ADDRESS, stablePeerAddress(host)),
                  equalTo(NETWORK_PEER_PORT, stablePeerPort(port)),
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
      NETWORK_PEER_ADDRESS,
      NETWORK_PEER_PORT,
      SERVER_ADDRESS,
      SERVER_PORT
    )
  }

  private def namespace(databaseIndex: Int): String =
    if (emitStableDatabaseSemconv()) databaseIndex.toString else null

  private def stablePeerAddress(value: String): String =
    if (emitStableDatabaseSemconv()) value else null

  private def stablePeerPort(value: JLong): JLong =
    if (emitStableDatabaseSemconv()) value else null

  private def createSentinelClient(
      sentinelHosts: Seq[String]
  ): SentinelMonitoredRedisClient = {
    val constructor = classOf[SentinelMonitoredRedisClient].getConstructors()(0)
    val options =
      if (constructor.getParameterCount == 8)
        Seq(Option.apply(null), Option.apply(null), Option.apply(null))
      else Seq(Option.apply(null), Option.apply(null))
    val arguments =
      Seq[Object](
        sentinelHosts.map((_, sentinelPort.intValue())),
        "mymaster"
      ) ++
        options ++
        Seq[Object](
          "SentinelMonitoredRedisClient",
          system,
          RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
        )
    constructor
      .newInstance(arguments: _*)
      .asInstanceOf[SentinelMonitoredRedisClient]
  }

  private def createSentinelMasterSlavesClient(
      sentinelHosts: Seq[String]
  ): SentinelMonitoredRedisClientMasterSlaves =
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

  private def sentinelTarget(sentinelHosts: Seq[String]): String =
    sentinelHosts.sorted
      .map(serverHost => s"$serverHost:$sentinelPort")
      .mkString(",") + "/mymaster"

  private def alternateHost(serverHost: String): String = {
    val resolvedHost = InetAddress.getByName(serverHost).getHostAddress
    if (resolvedHost == serverHost) "localhost" else resolvedHost
  }

  private def assertConfiguredTargetSpan(
      serverAddress: String,
      serverPort: JLong = null,
      operationName: String = "SET",
      networkPeerAddress: String = null,
      networkPeerPort: JLong = null,
      databaseIndex: String = null
  ): Unit =
    await().untilAsserted(new ThrowingRunnable {
      override def run(): Unit = {
        val serverSuffix =
          if (serverPort == null) serverAddress
          else s"$serverAddress:$serverPort"
        val expectedSpanName =
          if (serverSuffix == null) operationName
          else s"$operationName $serverSuffix"
        val span = testing
          .spans()
          .stream()
          .filter(new Predicate[SpanData] {
            override def test(span: SpanData): Boolean =
              span.getName == expectedSpanName
          })
          .findFirst()
          .orElse(null)
        assertThat(span).isNotNull
        assertThatSpan(span)
          .hasName(expectedSpanName)
          .hasKind(CLIENT)
          .hasAttributesSatisfyingExactly(
            equalTo(maybeStable(DB_SYSTEM), REDIS),
            equalTo(maybeStable(DB_OPERATION), operationName),
            equalTo(DB_NAMESPACE, databaseIndex),
            equalTo(
              NETWORK_PEER_ADDRESS,
              stablePeerAddress(networkPeerAddress)
            ),
            equalTo(NETWORK_PEER_PORT, stablePeerPort(networkPeerPort)),
            equalTo(SERVER_ADDRESS, serverAddress),
            equalTo(SERVER_PORT, serverPort)
          )
      }
    })

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
