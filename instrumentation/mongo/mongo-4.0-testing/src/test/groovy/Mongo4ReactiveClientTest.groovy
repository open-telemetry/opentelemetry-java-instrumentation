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
import org.junit.AssumptionViolatedException
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(10)
class Mongo4ReactiveClientTest extends AbstractMongoClientTest {

  @Shared
  MongoClient client

  def setup() throws Exception {
    client = MongoClients.create("mongodb://localhost:$port")
  }

  def cleanup() throws Exception {
    client?.close()
    client = null
  }

  @Override
  void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    db.createCollection(collectionName).subscribe(toSubscriber {})
  }

  @Override
  void createCollectionNoDescription(String dbName, String collectionName) {
    MongoDatabase db = MongoClients.create("mongodb://localhost:${port}").getDatabase(dbName)
    db.createCollection(collectionName).subscribe(toSubscriber {})
  }

  @Override
  void createCollectionWithAlreadyBuildClientOptions(String dbName, String collectionName) {
    throw new AssumptionViolatedException("not tested on 4.0")
  }

  @Override
  int getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    def count = new CompletableFuture<Integer>()
    db.getCollection(collectionName).estimatedDocumentCount().subscribe(toSubscriber { count.complete(it) })
    return count.join()
  }

  @Override
  int insert(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch1 = new CountDownLatch(1)
      // This creates a trace that isn't linked to the parent... using NIO internally that we don't handle.
      db.createCollection(collectionName).subscribe(toSubscriber { latch1.countDown() })
      latch1.await()
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(2)
    def count = new CompletableFuture<Integer>()
    collection.insertOne(new Document("password", "SECRET")).subscribe(toSubscriber {
      collection.estimatedDocumentCount().subscribe(toSubscriber { count.complete(it) })
    })
    return count.join()
  }

  @Override
  int update(String dbName, String collectionName) {
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
    def result = new CompletableFuture<UpdateResult>()
    def count = new CompletableFuture()
    collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW")))).subscribe(toSubscriber {
      result.complete(it)
      collection.estimatedDocumentCount().subscribe(toSubscriber { count.complete(it) })
    })
    return result.join().modifiedCount
  }

  @Override
  int delete(String dbName, String collectionName) {
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
    def result = new CompletableFuture<DeleteResult>()
    def count = new CompletableFuture()
    collection.deleteOne(new BsonDocument("password", new BsonString("SECRET"))).subscribe(toSubscriber {
      result.complete(it)
      collection.estimatedDocumentCount().subscribe(toSubscriber { count.complete(it) })
    })
    return result.join().deletedCount
  }

  @Override
  void getMore(String dbName, String collectionName) {
    throw new AssumptionViolatedException("not tested on reactive")
  }

  @Override
  void error(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch = new CountDownLatch(1)
      db.createCollection(collectionName).subscribe(toSubscriber {
        latch.countDown()
      })
      latch.await()
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)
    def result = new CompletableFuture<Throwable>()
    collection.updateOne(new BsonDocument(), new BsonDocument()).subscribe(toSubscriber {
      result.complete(it)
    })
    throw result.join()
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
        "$SemanticAttributes.DB_STATEMENT.key" {
          statementEval.call(it.replaceAll(" ", ""))
        }
        "$SemanticAttributes.DB_SYSTEM.key" "mongodb"
        "$SemanticAttributes.DB_NAME.key" dbName
        "$SemanticAttributes.DB_OPERATION.key" operation
        "$SemanticAttributes.DB_MONGODB_COLLECTION.key" collection
      }
    }
  }
}
