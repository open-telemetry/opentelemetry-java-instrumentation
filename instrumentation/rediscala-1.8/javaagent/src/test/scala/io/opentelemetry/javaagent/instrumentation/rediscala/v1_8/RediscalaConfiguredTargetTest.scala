/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rediscala

import io.opentelemetry.api.trace.SpanKind.CLIENT
import io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv
import io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension
import io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS
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
import org.testcontainers.containers.GenericContainer
import redis.{
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
import scala.concurrent.duration.Duration
import scala.concurrent.Await

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RediscalaConfiguredTargetTest {

  @RegisterExtension val testing = AgentInstrumentationExtension.create

  private val defaultDbIndex = 0
  private val masterName = "mymaster"

  var system: Object = null
  var redisServer: GenericContainer[_] = null
  var sentinelServer: GenericContainer[_] = null
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
      s"redis-server --port $port --daemonize yes && printf 'port 26379\\nsentinel resolve-hostnames yes\\nsentinel announce-hostnames yes\\nsentinel monitor $masterName $host $port 1\\n' > /tmp/sentinel.conf && exec redis-server /tmp/sentinel.conf --sentinel"
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
    val client = createMasterSlavesClient(master, slaves)
    try {
      val result = client.set("master-slaves-target", "value")
      Await.result(result, Duration("3 second"))
      assertConfiguredTargetSpan(
        (s"${master.host}:${master.port}" +:
          slaves.map(server => s"${server.host}:${server.port}").sorted)
          .mkString(","),
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
    val client = createMasterSlavesClient(master, slaves)
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
    val client = createMasterSlavesClient(master, slaves)
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

  private def namespace(databaseIndex: Int): String =
    if (emitStableDatabaseSemconv()) databaseIndex.toString else null

  private def createMasterSlavesClient(
      master: RedisServer,
      slaves: Seq[RedisServer]
  ): RedisClientMasterSlaves =
    classOf[RedisClientMasterSlaves].getConstructors
      .find(_.getParameterCount == 4)
      .get
      .newInstance(
        master,
        slaves,
        system,
        RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )
      .asInstanceOf[RedisClientMasterSlaves]

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
        masterName
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
        masterName,
        system,
        RedisDispatcher("rediscala.rediscala-client-worker-dispatcher")
      )
      .asInstanceOf[SentinelMonitoredRedisClientMasterSlaves]

  private def sentinelTarget(sentinelHosts: Seq[String]): String =
    sentinelHosts.sorted
      .map(serverHost => s"$serverHost:$sentinelPort")
      .mkString(",") + s"/$masterName"

  private def alternateHost(serverHost: String): String = {
    val resolvedHost = InetAddress.getByName(serverHost).getHostAddress
    if (resolvedHost == serverHost) "localhost" else resolvedHost
  }

  private def assertConfiguredTargetSpan(
      serverAddress: String,
      serverPort: JLong = null,
      operationName: String = "SET",
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
        assertThat(testing.spans(): java.lang.Iterable[SpanData])
          .filteredOn(new Predicate[SpanData] {
            override def test(span: SpanData): Boolean =
              span.getName == expectedSpanName
          })
          .singleElement()
          .satisfies(new Consumer[SpanData] {
            override def accept(span: SpanData): Unit =
              OpenTelemetryAssertions
                .assertThat(span)
                .hasName(expectedSpanName)
                .hasKind(CLIENT)
                .hasAttributesSatisfyingExactly(
                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                  equalTo(maybeStable(DB_OPERATION), operationName),
                  equalTo(DB_NAMESPACE, databaseIndex),
                  equalTo(NETWORK_PEER_ADDRESS, null),
                  equalTo(NETWORK_PEER_PORT, null),
                  equalTo(SERVER_ADDRESS, serverAddress),
                  equalTo(SERVER_PORT, serverPort)
                )
          })
      }
    })
}
