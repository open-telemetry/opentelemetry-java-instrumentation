/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.testing

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Shared

abstract class AbstractMongoClientTest<T> extends InstrumentationSpecification {

  @Shared
  GenericContainer mongodb

  @Shared
  int port

  def setupSpec() {
    mongodb = new GenericContainer("mongo:3.2")
      .withExposedPorts(27017)
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("mongodb")))
    mongodb.start()

    port = mongodb.getMappedPort(27017)
  }

  def cleanupSpec() throws Exception {
    mongodb.stop()
  }

  // Different client versions have different APIs to do these operations. If adding a test for a new
  // version, refer to existing ones on how to implement these operations.

  abstract void createCollection(String dbName, String collectionName)

  abstract void createCollectionNoDescription(String dbName, String collectionName)

  // Tests the fix for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/457
  // TracingCommandListener might get added multiple times if clientOptions are built using existing clientOptions or when calling a build method twice.
  // This test asserts that duplicate traces are not created in those cases.
  abstract void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName)

  abstract void createCollectionCallingBuildTwice(String dbName, String collectionName)

  abstract int getCollection(String dbName, String collectionName)

  abstract T setupInsert(String dbName, String collectionName)

  abstract int insert(T collection)

  abstract T setupUpdate(String dbName, String collectionName)

  abstract int update(T collection)

  abstract T setupDelete(String dbName, String collectionName)

  abstract int delete(T collection)

  abstract T setupGetMore(String dbName, String collectionName)

  abstract void getMore(T collection)

  abstract void error(String dbName, String collectionName)

  def "test port open"() {
    when:
    new Socket("localhost", port)

    then:
    noExceptionThrown()
  }

  def "test create collection"() {
    when:
    runWithSpan("parent") {
      createCollection(dbName, collectionName)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "create", collectionName, dbName, span(0)) {
          assert it == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test create collection no description"() {
    when:
    runWithSpan("parent") {
      createCollectionNoDescription(dbName, collectionName)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "create", collectionName, dbName, span(0), {
          assert it == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        })
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test create collection calling build twice"() {
    when:
    runWithSpan("parent") {
      createCollectionCallingBuildTwice(dbName, collectionName)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "create", collectionName, dbName, span(0)) {
          assert it == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test get collection"() {
    when:
    def count = runWithSpan("parent") {
      getCollection(dbName, collectionName)
    }

    then:
    count == 0
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "count", collectionName, dbName, span(0)) {
          assert it == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\":\"$collectionName\"}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test insert"() {
    when:
    def collection = setupInsert(dbName, collectionName)
    def count = runWithSpan("parent") {
      insert(collection)
    }

    then:
    count == 1
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "insert", collectionName, dbName, span(0)) {
          assert it == "{\"insert\":\"$collectionName\",\"ordered\":\"?\",\"documents\":[{\"_id\":\"?\",\"password\":\"?\"}]}" ||
            it == "{\"insert\": \"$collectionName\", \"ordered\": \"?\", \"\$db\": \"?\", \"documents\": [{\"_id\": \"?\", \"password\": \"?\"}]}"
          true
        }
        mongoSpan(it, 2, "count", collectionName, dbName, span(0)) {
          assert it == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\":\"$collectionName\"}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test update"() {
    when:
    def collection = setupUpdate(dbName, collectionName)
    int modifiedCount = runWithSpan("parent") {
      update(collection)
    }

    then:
    modifiedCount == 1
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "update", collectionName, dbName, span(0)) {
          assert it == "{\"update\":\"$collectionName\",\"ordered\":\"?\",\"updates\":[{\"q\":{\"password\":\"?\"},\"u\":{\"\$set\":{\"password\":\"?\"}}}]}" ||
            it == "{\"update\": \"?\", \"ordered\": \"?\", \"\$db\": \"?\", \"updates\": [{\"q\": {\"password\": \"?\"}, \"u\": {\"\$set\": {\"password\": \"?\"}}}]}"
          true
        }
        mongoSpan(it, 2, "count", collectionName, dbName, span(0)) {
          assert it == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\":\"$collectionName\"}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test delete"() {
    when:
    def collection = setupDelete(dbName, collectionName)
    int deletedCount = runWithSpan("parent") {
      delete(collection)
    }

    then:
    deletedCount == 1
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "delete", collectionName, dbName, span(0)) {
          assert it == "{\"delete\":\"$collectionName\",\"ordered\":\"?\",\"deletes\":[{\"q\":{\"password\":\"?\"},\"limit\":\"?\"}]}" ||
            it == "{\"delete\": \"?\", \"ordered\": \"?\", \"\$db\": \"?\", \"deletes\": [{\"q\": {\"password\": \"?\"}, \"limit\": \"?\"}]}"
          true
        }
        mongoSpan(it, 2, "count", collectionName, dbName, span(0)) {
          assert it == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\":\"$collectionName\"}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test collection name for getMore command"() {
    when:
    def collection = setupGetMore(dbName, collectionName)
    runWithSpan("parent") {
      getMore(collection)
    }

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "find", collectionName, dbName, span(0)) {
          assert it == '{"find":"' + collectionName + '","filter":{"_id":{"$gte":"?"}},"batchSize":"?"}'
          true
        }
        mongoSpan(it, 2, "getMore", collectionName, dbName, span(0)) {
          assert it == '{"getMore":"?","collection":"?","batchSize":"?"}'
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test error"() {
    when:
    error(dbName, collectionName)

    then:
    thrown(IllegalArgumentException)
    // Unfortunately not caught by our instrumentation.
    assertTraces(0) {}

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  def "test create collection with already built ClientOptions"() {
    when:
    runWithSpan("parent") {
      createCollectionWithAlreadyBuiltClientOptions(dbName, collectionName)
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        mongoSpan(it, 1, "create", collectionName, dbName, span(0)) {
          assert it == "{\"create\":\"$collectionName\",\"capped\":\"?\"}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }

  private static final AtomicInteger collectionIndex = new AtomicInteger()

  def createCollectionName() {
    return "testCollection-${collectionIndex.getAndIncrement()}"
  }

  def mongoSpan(TraceAssert trace, int index,
                String operation, String collection,
                String dbName, Object parentSpan,
                Closure<Boolean> statementEval) {
    trace.span(index) {
      name operation + " " + dbName + "." + collection
      kind CLIENT
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      attributes {
        "$SemanticAttributes.NET_PEER_NAME" "localhost"
        "$SemanticAttributes.NET_PEER_IP" "127.0.0.1"
        "$SemanticAttributes.NET_PEER_PORT" port
        "$SemanticAttributes.DB_STATEMENT" {
          statementEval.call(it.replaceAll(" ", ""))
        }
        "$SemanticAttributes.DB_SYSTEM" "mongodb"
        "$SemanticAttributes.DB_CONNECTION_STRING" "mongodb://localhost:" + port
        "$SemanticAttributes.DB_NAME" dbName
        "$SemanticAttributes.DB_OPERATION" operation
        "$SemanticAttributes.DB_MONGODB_COLLECTION" collection
      }
    }
  }
}
