/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Shared

abstract class AbstractMongoClientTest extends AgentInstrumentationSpecification {

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
  // TracingCommandListener might get added multiple times if clientOptions are built using existing clientOptions or when calling  a build method twice.
  // This test asserts that duplicate traces are not created in those cases.
  abstract void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName)

  abstract int getCollection(String dbName, String collectionName)

  abstract int insert(String dbName, String collectionName)

  abstract int update(String dbName, String collectionName)

  abstract int delete(String dbName, String collectionName)

  abstract void getMore(String dbName, String collectionName)

  abstract void error(String dbName, String collectionName)

  def "test port open"() {
    when:
    new Socket("localhost", port)

    then:
    noExceptionThrown()
  }

  def "test create collection"() {
    when:
    createCollection(dbName, collectionName)

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "create", collectionName, dbName) {
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
    createCollectionNoDescription(dbName, collectionName)

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "create", collectionName, dbName, {
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

  def "test get collection"() {
    when:
    def count = getCollection(dbName, collectionName)

    then:
    count == 0
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "count", collectionName, dbName) {
          assert it == "{\"count\":\"$collectionName\",\"query\":{}}" ||
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
    def count = insert(dbName, collectionName)

    then:
    count == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "insert", collectionName, dbName) {
          assert it == "{\"insert\":\"$collectionName\",\"ordered\":\"?\",\"documents\":[{\"_id\":\"?\",\"password\":\"?\"}]}" ||
            it == "{\"insert\": \"$collectionName\", \"ordered\": \"?\", \"\$db\": \"?\", \"documents\": [{\"_id\": \"?\", \"password\": \"?\"}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0, "count", collectionName, dbName) {
          assert it == "{\"count\":\"$collectionName\",\"query\":{}}" ||
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
    int modifiedCount = update(dbName, collectionName)

    then:
    modifiedCount == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "update", collectionName, dbName) {
          assert it == "{\"update\":\"$collectionName\",\"ordered\":\"?\",\"updates\":[{\"q\":{\"password\":\"?\"},\"u\":{\"\$set\":{\"password\":\"?\"}}}]}" ||
            it == "{\"update\": \"?\", \"ordered\": \"?\", \"\$db\": \"?\", \"updates\": [{\"q\": {\"password\": \"?\"}, \"u\": {\"\$set\": {\"password\": \"?\"}}}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0, "count", collectionName, dbName) {
          assert it == "{\"count\":\"$collectionName\",\"query\":{}}" ||
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
    int deletedCount = delete(dbName, collectionName)

    then:
    deletedCount == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "delete", collectionName, dbName) {
          assert it == "{\"delete\":\"$collectionName\",\"ordered\":\"?\",\"deletes\":[{\"q\":{\"password\":\"?\"},\"limit\":\"?\"}]}" ||
            it == "{\"delete\": \"?\", \"ordered\": \"?\", \"\$db\": \"?\", \"deletes\": [{\"q\": {\"password\": \"?\"}, \"limit\": \"?\"}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0, "count", collectionName, dbName) {
          assert it == "{\"count\":\"$collectionName\",\"query\":{}}" ||
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
    getMore(dbName, collectionName)

    then:
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "find", collectionName, dbName) {
          it == '{"find":"' + collectionName + '","filter":{"_id":{"$gte":"?"}},"batchSize":"?"}'
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0, "getMore", collectionName, dbName) {
          it == '{"getMore":"?","collection":"?","batchSize":"?"}'
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
    createCollectionWithAlreadyBuiltClientOptions(dbName, collectionName)

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "create", collectionName, dbName) {
          it == "{\"create\":\"$collectionName\",\"capped\":\"?\"}"
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
                String dbName, Closure<Boolean> statementEval,
                Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      name { operation + " " + dbName + "." + collection }
      kind CLIENT
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      attributes {
        "$SemanticAttributes.NET_PEER_NAME.key" "localhost"
        "$SemanticAttributes.NET_PEER_IP.key" "127.0.0.1"
        "$SemanticAttributes.NET_PEER_PORT.key" port
        "$SemanticAttributes.DB_STATEMENT.key" {
          statementEval.call(it.replaceAll(" ", ""))
        }
        "$SemanticAttributes.DB_SYSTEM.key" "mongodb"
        "$SemanticAttributes.DB_CONNECTION_STRING.key" "mongodb://localhost:" + port
        "$SemanticAttributes.DB_NAME.key" dbName
        "$SemanticAttributes.DB_OPERATION.key" operation
        "$SemanticAttributes.DB_MONGODB_COLLECTION.key" collection
      }
    }
  }
}
