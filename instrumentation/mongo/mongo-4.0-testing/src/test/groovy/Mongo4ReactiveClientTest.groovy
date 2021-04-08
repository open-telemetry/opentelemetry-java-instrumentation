/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import com.mongodb.reactivestreams.client.MongoDatabase
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import org.junit.AssumptionViolatedException
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Shared

class Mongo4ReactiveClientTest extends AbstractMongoClientTest {

  @Shared
  MongoClient client

  def setupSpec() throws Exception {
    client = MongoClients.create("mongodb://localhost:$port")
  }

  def cleanupSpec() throws Exception {
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
  void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName) {
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
      db.createCollection(collectionName).subscribe(toSubscriber { latch1.countDown() })
      latch1.await()
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)
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
}
