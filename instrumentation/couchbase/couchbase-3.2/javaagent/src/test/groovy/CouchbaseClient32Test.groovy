/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.couchbase.client.core.error.DocumentNotFoundException
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.Collection
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.couchbase.BucketDefinition
import org.testcontainers.couchbase.CouchbaseContainer
import org.testcontainers.couchbase.CouchbaseService
import spock.lang.Shared

import java.time.Duration

// Couchbase instrumentation is owned upstream so we don't assert on the contents of the spans, only
// that the instrumentation is properly registered by the agent, meaning some spans were generated.
class CouchbaseClient32Test extends AgentInstrumentationSpecification {
  private static final Logger logger = LoggerFactory.getLogger("couchbase-container")

  @Shared
  CouchbaseContainer couchbase
  @Shared
  Cluster cluster
  @Shared
  Collection collection

  def setupSpec() {
    couchbase = new CouchbaseContainer()
      .withExposedPorts(8091)
      .withEnabledServices(CouchbaseService.KV)
      .withBucket(new BucketDefinition("test"))
      .withLogConsumer(new Slf4jLogConsumer(logger))
      .withStartupTimeout(Duration.ofSeconds(120))
    couchbase.start()

    cluster = Cluster.connect(couchbase.connectionString, couchbase.username, couchbase.password)
    def bucket = cluster.bucket("test")
    collection = bucket.defaultCollection()
    bucket.waitUntilReady(Duration.ofSeconds(30))
  }

  def cleanupSpec() {
    couchbase.stop()
  }

  def "emits spans"() {
    when:
    try {
      collection.get("id")
    } catch (DocumentNotFoundException e) {
      // Expected
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name(~/.*get/)
        }
        span(1) {
          name(~/.*dispatch_to_server/)
        }
      }
    }

    cleanup:
    cluster.disconnect()
  }
}
