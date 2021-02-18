/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.mongodb.ConnectionString
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoClient
import com.mongodb.async.client.MongoClientSettings
import com.mongodb.async.client.MongoClients
import com.mongodb.async.client.MongoCollection
import com.mongodb.async.client.MongoDatabase
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.connection.ClusterSettings
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(10)
class MongoAsyncClientTest extends MongoBaseTest {

  @Shared
  MongoClient client

  def setup() throws Exception {
    client = MongoClients.create(
      MongoClientSettings.builder()
        .clusterSettings(
          ClusterSettings.builder()
            .description("some-description")
            .applyConnectionString(new ConnectionString("mongodb://localhost:$port"))
            .build())
        .build())
  }

  def cleanup() throws Exception {
    client?.close()
    client = null
  }

  def "test create collection"() {
    setup:
    MongoDatabase db = client.getDatabase(dbName)

    when:
    db.createCollection(collectionName, toCallback {})

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "create", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  // Tests the fix for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/457
  // TracingCommandListener might get added multiple times if ClientSettings are built using existing ClientSettings or when calling a build method twice.
  // This test asserts that duplicate traces are not created in those cases.
  def "test create collection with already built ClientSettings"() {
    setup:
    def clientSettings = client.settings
    def newClientSettings = MongoClientSettings.builder(clientSettings).build()
    MongoDatabase db = MongoClients.create(newClientSettings).getDatabase(dbName)

    when:
    db.createCollection(collectionName, toCallback {})

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "create", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test create collection no description"() {
    setup:
    MongoDatabase db = MongoClients.create("mongodb://localhost:$port").getDatabase(dbName)

    when:
    db.createCollection(collectionName, toCallback {})

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "create", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test get collection"() {
    setup:
    MongoDatabase db = client.getDatabase(dbName)

    when:
    def count = new CompletableFuture()
    db.getCollection(collectionName).count toCallback { count.complete(it) }

    then:
    count.get() == 0
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "count", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test insert"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch1 = new CountDownLatch(1)
      db.createCollection(collectionName, toCallback { latch1.countDown() })
      latch1.await()
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)

    when:
    def count = new CompletableFuture()
    collection.insertOne(new Document("password", "SECRET"), toCallback {
      collection.count toCallback { count.complete(it) }
    })

    then:
    count.get() == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "insert", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"insert\":\"$collectionName\",\"ordered\":\"?\",\"documents\":[{\"_id\":\"?\",\"password\":\"?\"}]}" ||
            it == "{\"insert\": \"$collectionName\", \"ordered\": \"?\", \"\$db\": \"?\", \"documents\": [{\"_id\": \"?\", \"password\": \"?\"}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0, "count", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test update"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch1 = new CountDownLatch(1)
      db.createCollection(collectionName, toCallback { latch1.countDown() })
      latch1.await()
      def coll = db.getCollection(collectionName)
      def latch2 = new CountDownLatch(1)
      coll.insertOne(new Document("password", "OLDPW"), toCallback { latch2.countDown() })
      latch2.await()
      return coll
    }
    ignoreTracesAndClear(1)

    when:
    def result = new CompletableFuture<UpdateResult>()
    def count = new CompletableFuture()
    collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW"))), toCallback {
      result.complete(it)
      collection.count toCallback { count.complete(it) }
    })

    then:
    result.get().modifiedCount == 1
    count.get() == 1
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "update", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"update\":\"$collectionName\",\"ordered\":\"?\",\"updates\":[{\"q\":{\"password\":\"?\"},\"u\":{\"\$set\":{\"password\":\"?\"}}}]}" ||
            it == "{\"update\": \"?\", \"ordered\": \"?\", \"\$db\": \"?\", \"updates\": [{\"q\": {\"password\": \"?\"}, \"u\": {\"\$set\": {\"password\": \"?\"}}}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0, "count", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  def "test delete"() {
    setup:
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch1 = new CountDownLatch(1)
      db.createCollection(collectionName, toCallback { latch1.countDown() })
      latch1.await()
      def coll = db.getCollection(collectionName)
      def latch2 = new CountDownLatch(1)
      coll.insertOne(new Document("password", "SECRET"), toCallback { latch2.countDown() })
      latch2.await()
      return coll
    }
    ignoreTracesAndClear(1)

    when:
    def result = new CompletableFuture<DeleteResult>()
    def count = new CompletableFuture()
    collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")), toCallback {
      result.complete(it)
      collection.count toCallback { count.complete(it) }
    })

    then:
    result.get().deletedCount == 1
    count.get() == 0
    assertTraces(2) {
      trace(0, 1) {
        mongoSpan(it, 0, "delete", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"delete\":\"$collectionName\",\"ordered\":\"?\",\"deletes\":[{\"q\":{\"password\":\"?\"},\"limit\":\"?\"}]}" ||
            it == "{\"delete\": \"?\", \"ordered\": \"?\", \"\$db\": \"?\", \"deletes\": [{\"q\": {\"password\": \"?\"}, \"limit\": \"?\"}]}"
          true
        }
      }
      trace(1, 1) {
        mongoSpan(it, 0, "count", collectionName, dbName) {
          assert it.replaceAll(" ", "") == "{\"count\":\"$collectionName\",\"query\":{}}" ||
            it == "{\"count\": \"$collectionName\", \"query\": {}, \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        }
      }
    }

    where:
    dbName = "test_db"
    collectionName = "testCollection"
  }

  SingleResultCallback toCallback(Closure closure) {
    return new SingleResultCallback() {
      @Override
      void onResult(Object result, Throwable t) {
        if (t) {
          closure.call(t)
        } else {
          closure.call(result)
        }
      }
    }
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
        "$SemanticAttributes.DB_CONNECTION_STRING.key" "mongodb://localhost:" + port
        "$SemanticAttributes.DB_STATEMENT.key" statementEval
        "$SemanticAttributes.DB_SYSTEM.key" "mongodb"
        "$SemanticAttributes.DB_NAME.key" dbName
        "$SemanticAttributes.DB_OPERATION.key" operation
        "$SemanticAttributes.DB_MONGODB_COLLECTION.key" collection
      }
    }
  }
}
