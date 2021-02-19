/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import com.mongodb.reactivestreams.client.MongoDatabase
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(10)
class Mongo4ReactiveClientTest extends MongoBaseTest {

  @Shared
  MongoClient client

  def setup() throws Exception {
    client = MongoClients.create("mongodb://localhost:$port")
  }

  def cleanup() throws Exception {
    client?.close()
    client = null
  }

  def "test create collection"() {
    setup:
    MongoDatabase db = client.getDatabase(dbName)

    when:
    db.createCollection(collectionName).subscribe(toSubscriber {})

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
    db.createCollection(collectionName).subscribe(toSubscriber {})

    then:
    assertTraces(1) {
      trace(0, 1) {
        mongoSpan(it, 0, "create", collectionName, dbName, {
          assert it.replaceAll(" ", "") == "{\"create\":\"$collectionName\",\"capped\":\"?\"}" ||
            it == "{\"create\": \"$collectionName\", \"capped\": \"?\", \"\$db\": \"?\", \"\$readPreference\": {\"mode\": \"?\"}}"
          true
        })
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
    db.getCollection(collectionName).estimatedDocumentCount().subscribe(toSubscriber { count.complete(it) })

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
      // This creates a trace that isn't linked to the parent... using NIO internally that we don't handle.
      db.createCollection(collectionName).subscribe(toSubscriber { latch1.countDown() })
      latch1.await()
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(2)

    when:
    def count = new CompletableFuture()
    collection.insertOne(new Document("password", "SECRET")).subscribe(toSubscriber {
      collection.estimatedDocumentCount().subscribe(toSubscriber { count.complete(it) })
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
      db.createCollection(collectionName).subscribe(toSubscriber { latch1.countDown() })
      latch1.await()
      def coll = db.getCollection(collectionName)
      def latch2 = new CountDownLatch(1)
      coll.insertOne(new Document("password", "OLDPW")).subscribe(toSubscriber { latch2.countDown() })
      latch2.await()
      return coll
    }
    ignoreTracesAndClear(1)

    when:
    def result = new CompletableFuture<UpdateResult>()
    def count = new CompletableFuture()
    collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW")))).subscribe(toSubscriber {
      result.complete(it)
      collection.estimatedDocumentCount().subscribe(toSubscriber { count.complete(it) })
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
      db.createCollection(collectionName).subscribe(toSubscriber { latch1.countDown() })
      latch1.await()
      def coll = db.getCollection(collectionName)
      def latch2 = new CountDownLatch(1)
      coll.insertOne(new Document("password", "SECRET")).subscribe(toSubscriber { latch2.countDown() })
      latch2.await()
      return coll
    }
    ignoreTracesAndClear(1)

    when:
    def result = new CompletableFuture<DeleteResult>()
    def count = new CompletableFuture()
    collection.deleteOne(new BsonDocument("password", new BsonString("SECRET"))).subscribe(toSubscriber {
      result.complete(it)
      collection.estimatedDocumentCount().subscribe(toSubscriber { count.complete(it) })
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

  def Subscriber<?> toSubscriber(Closure closure) {
    return new Subscriber() {
      boolean hasResult

      @Override
      void onSubscribe(Subscription s) {
        s.request(1) // must request 1 value to trigger async call
      }

      @Override
      void onNext(Object o) { hasResult = true; closure.call(o) }

      @Override
      void onError(Throwable t) { hasResult = true; closure.call(t) }

      @Override
      void onComplete() {
        if (!hasResult) {
          hasResult = true
          closure.call()
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
