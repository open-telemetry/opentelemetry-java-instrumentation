package util

import com.couchbase.client.core.metrics.DefaultLatencyMetricsCollectorConfig
import com.couchbase.client.core.metrics.DefaultMetricsCollectorConfig
import com.couchbase.client.java.bucket.BucketType
import com.couchbase.client.java.cluster.BucketSettings
import com.couchbase.client.java.cluster.DefaultBucketSettings
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment
import com.couchbase.mock.Bucket
import com.couchbase.mock.BucketConfiguration
import com.couchbase.mock.CouchbaseMock
import com.couchbase.mock.http.query.QueryServer
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import spock.lang.Shared

import java.util.concurrent.TimeUnit

abstract class AbstractCouchbaseTest extends AgentTestRunner {

  static final USERNAME = "Administrator"
  static final PASSWORD = "password"

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

  def setupSpec() {
    mock = new CouchbaseMock("127.0.0.1", port, 1, 1)
    mock.httpServer.register("/query", new QueryServer())
    mock.start()
    println "CouchbaseMock listening on localhost:$port"

    mock.createBucket(convert(bucketCouchbase))
    mock.createBucket(convert(bucketMemcache))

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
    mock?.stop()

    System.clearProperty(Config.PREFIX + Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE)
  }

  protected DefaultCouchbaseEnvironment.Builder envBuilder(BucketSettings bucketSettings) {
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

  void sortAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {

    TEST_WRITER.waitForTraces(size)

    TEST_WRITER.each {
      it.sort({
        a, b ->
          boolean aIsCouchbaseOperation = a.operationName == "couchbase.call"
          boolean bIsCouchbaseOperation = b.operationName == "couchbase.call"

          if (aIsCouchbaseOperation && !bIsCouchbaseOperation) {
            return 1
          } else if (!aIsCouchbaseOperation && bIsCouchbaseOperation) {
            return -1
          }

          return a.resourceName.compareTo(b.resourceName)
      })
    }

    assertTraces(size, spec)
  }

  void assertCouchbaseCall(TraceAssert trace, int index, String name, String bucketName = null, Object parentSpan = null) {
    trace.span(index) {
      serviceName "couchbase"
      resourceName name
      operationName "couchbase.call"
      spanType DDSpanTypes.COUCHBASE
      errored false
      if (parentSpan == null) {
        parent()
      } else {
        childOf((DDSpan) parentSpan)
      }
      tags {
        "$Tags.COMPONENT" "couchbase-client"
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
        "$Tags.DB_TYPE" "couchbase"
        if (bucketName != null) {
          "bucket" bucketName
        }
        defaultTags()
      }
    }
  }
}
