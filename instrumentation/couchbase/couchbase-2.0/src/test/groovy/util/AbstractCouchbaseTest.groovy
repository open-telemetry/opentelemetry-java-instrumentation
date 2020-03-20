/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import io.opentelemetry.auto.config.Config
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.sdk.trace.data.SpanData
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static io.opentelemetry.trace.Span.Kind.CLIENT

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

  void assertCouchbaseCall(TraceAssert trace, int index, String name, String bucketName = null, Object dbStatement = null, Object parentSpan = null) {
    trace.span(index) {
      operationName "couchbase.call"
      spanKind CLIENT
      errored false
      if (parentSpan == null) {
        parent()
      } else {
        childOf((SpanData) parentSpan)
      }
      tags {
        "$MoreTags.SERVICE_NAME" "couchbase"
        "$MoreTags.RESOURCE_NAME" name
        "$Tags.COMPONENT" "couchbase-client"
        "$Tags.DB_TYPE" "couchbase"
        if (bucketName != null) {
          "$Tags.DB_INSTANCE" bucketName
        }
        if (dbStatement != null) {
          "$Tags.DB_STATEMENT" dbStatement
        }
      }
    }
  }
}
