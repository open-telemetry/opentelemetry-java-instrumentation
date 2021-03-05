/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package util

import static io.opentelemetry.api.trace.SpanKind.CLIENT

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
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.TimeUnit
import spock.lang.Shared

abstract class AbstractCouchbaseTest extends AgentInstrumentationSpecification {

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

  void assertCouchbaseCall(TraceAssert trace, int index, Object spanName, String bucketName = null, Object parentSpan = null, Object statement = null) {
    trace.span(index) {
      name spanName
      kind CLIENT
      errored false
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      attributes {
        "${SemanticAttributes.DB_SYSTEM.key}" "couchbase"
        if (bucketName != null) {
          "${SemanticAttributes.DB_NAME.key}" bucketName
        }
        "${SemanticAttributes.DB_STATEMENT.key}" (statement ?: spanName)
      }
    }
  }
}
