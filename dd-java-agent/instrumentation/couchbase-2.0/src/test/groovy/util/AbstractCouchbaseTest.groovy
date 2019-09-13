package util


import com.couchbase.client.core.metrics.DefaultLatencyMetricsCollectorConfig
import com.couchbase.client.core.metrics.DefaultMetricsCollectorConfig
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.bucket.BucketType
import com.couchbase.client.java.cluster.BucketSettings
import com.couchbase.client.java.cluster.ClusterManager
import com.couchbase.client.java.cluster.DefaultBucketSettings
import com.couchbase.client.java.env.CouchbaseEnvironment
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment
import com.couchbase.mock.Bucket
import com.couchbase.mock.BucketConfiguration
import com.couchbase.mock.CouchbaseMock
import com.couchbase.mock.http.query.QueryServer
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.Config
import spock.lang.Shared

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

abstract class AbstractCouchbaseTest extends AgentTestRunner {

  private static final USERNAME = "Administrator"
  private static final PASSWORD = "password"

  @Shared
  private int port = PortUtils.randomOpenPort()

  @Shared
  private String testBucketName = this.getClass().simpleName

  @Shared
  protected bucketCouchbase = DefaultBucketSettings.builder()
    .enableFlush(true)
    .name("$testBucketName-cb")
    .password("test-pass")
    .type(BucketType.COUCHBASE)
    .quota(100)
    .build()

  @Shared
  protected bucketMemcache = DefaultBucketSettings.builder()
    .enableFlush(true)
    .name("$testBucketName-mem")
    .password("test-pass")
    .type(BucketType.MEMCACHED)
    .quota(100)
    .build()

  @Shared
  CouchbaseMock mock
  @Shared
  protected CouchbaseCluster couchbaseCluster
  @Shared
  protected CouchbaseCluster memcacheCluster
  @Shared
  protected CouchbaseEnvironment couchbaseEnvironment
  @Shared
  protected CouchbaseEnvironment memcacheEnvironment
  @Shared
  protected ClusterManager couchbaseManager
  @Shared
  protected ClusterManager memcacheManager

  def setupSpec() {

    mock = new CouchbaseMock("127.0.0.1", port, 1, 1)
    mock.httpServer.register("/query", new QueryServer())
    mock.start()
    println "CouchbaseMock listening on localhost:$port"

    mock.createBucket(convert(bucketCouchbase))

    couchbaseEnvironment = envBuilder(bucketCouchbase).build()
    couchbaseCluster = CouchbaseCluster.create(couchbaseEnvironment, Arrays.asList("127.0.0.1"))
    couchbaseManager = couchbaseCluster.clusterManager(USERNAME, PASSWORD)

    mock.createBucket(convert(bucketMemcache))

    memcacheEnvironment = envBuilder(bucketMemcache).build()
    memcacheCluster = CouchbaseCluster.create(memcacheEnvironment, Arrays.asList("127.0.0.1"))
    memcacheManager = memcacheCluster.clusterManager(USERNAME, PASSWORD)

    // Cache buckets:
    couchbaseCluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password())
    memcacheCluster.openBucket(bucketMemcache.name(), bucketMemcache.password())

    // This setting should have no effect since decorator returns null for the instance.
    System.setProperty(Config.PREFIX + Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
  }

  private static BucketConfiguration convert(BucketSettings bucketSettings) {
    def configuration = new BucketConfiguration()
    configuration.name = bucketSettings.name()
    configuration.password = bucketSettings.password()
    configuration.type = Bucket.BucketType.valueOf(bucketSettings.type().name())
    configuration.numNodes = 1
    configuration.numReplicas = 0
    return configuration
  }

  def cleanupSpec() {
    try {
      couchbaseCluster?.disconnect()
    } catch (RejectedExecutionException e) {
      // already closed by a test?
    }
    try {
      memcacheCluster?.disconnect()
    } catch (RejectedExecutionException e) {
      // already closed by a test?
    }

    mock?.stop()

    System.clearProperty(Config.PREFIX + Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE)
  }

  private DefaultCouchbaseEnvironment.Builder envBuilder(BucketSettings bucketSettings) {
    // Couchbase seems to be really slow to start sometimes
    def timeout = TimeUnit.SECONDS.toMillis(20)
    return DefaultCouchbaseEnvironment.builder()
      .bootstrapCarrierDirectPort(mock.getCarrierPort(bucketSettings.name()))
      .bootstrapHttpDirectPort(port)
    // settings to try to reduce variability in the tests:
      .runtimeMetricsCollectorConfig(DefaultMetricsCollectorConfig.create(0, TimeUnit.DAYS))
      .networkLatencyMetricsCollectorConfig(DefaultLatencyMetricsCollectorConfig.create(0, TimeUnit.DAYS))
      .computationPoolSize(1)
      .connectTimeout(timeout)
      .disconnectTimeout(timeout)
      .kvTimeout(timeout)
      .managementTimeout(timeout)
      .queryTimeout(timeout)
      .viewTimeout(timeout)
      .keepAliveTimeout(timeout)
      .searchTimeout(timeout)
      .analyticsTimeout(timeout)
      .socketConnectTimeout(timeout.intValue())
  }
}
