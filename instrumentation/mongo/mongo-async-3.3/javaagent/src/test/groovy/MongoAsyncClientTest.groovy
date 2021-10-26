/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

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
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import org.opentest4j.TestAbortedException
import spock.lang.Shared

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class MongoAsyncClientTest extends AbstractMongoClientTest<MongoCollection<Document>> implements AgentTestTrait {

  @Shared
  MongoClient client

  def setupSpec() throws Exception {
    client = MongoClients.create(
      MongoClientSettings.builder()
        .clusterSettings(
          ClusterSettings.builder()
            .description("some-description")
            .applyConnectionString(new ConnectionString("mongodb://localhost:$port"))
            .build())
        .build())
  }

  def cleanupSpec() throws Exception {
    client?.close()
    client = null
  }

  @Override
  void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    db.createCollection(collectionName, toCallback {})
  }

  @Override
  void createCollectionNoDescription(String dbName, String collectionName) {
    MongoDatabase db = MongoClients.create("mongodb://localhost:$port").getDatabase(dbName)
    db.createCollection(collectionName, toCallback {})
  }

  @Override
  void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName) {
    def clientSettings = client.settings
    def newClientSettings = MongoClientSettings.builder(clientSettings).build()
    MongoDatabase db = MongoClients.create(newClientSettings).getDatabase(dbName)
    db.createCollection(collectionName, toCallback {})
  }

  @Override
  void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    def settings = MongoClientSettings.builder()
      .clusterSettings(
        ClusterSettings.builder()
          .description("some-description")
          .applyConnectionString(new ConnectionString("mongodb://localhost:$port"))
          .build())
    settings.build()
    MongoDatabase db = MongoClients.create(settings.build()).getDatabase(dbName)
    db.createCollection(collectionName, toCallback {})
  }

  @Override
  int getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    def count = new CompletableFuture<Integer>()
    db.getCollection(collectionName).count toCallback { count.complete(it) }
    return count.join()
  }

  @Override
  MongoCollection<Document> setupInsert(String dbName, String collectionName) {
    MongoCollection<Document> collection = runWithSpan("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch1 = new CountDownLatch(1)
      db.createCollection(collectionName, toCallback { latch1.countDown() })
      latch1.await()
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)
    return collection
  }

  @Override
  int insert(MongoCollection<Document> collection) {
    def count = new CompletableFuture<Integer>()
    collection.insertOne(new Document("password", "SECRET"), toCallback {
      collection.count toCallback { count.complete(it) }
    })
    return count.get()
  }

  @Override
  MongoCollection<Document> setupUpdate(String dbName, String collectionName) {
    MongoCollection<Document> collection = runWithSpan("setup") {
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
    return collection
  }

  @Override
  int update(MongoCollection<Document> collection) {
    def result = new CompletableFuture<UpdateResult>()
    def count = new CompletableFuture()
    collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW"))), toCallback {
      result.complete(it)
      collection.count toCallback { count.complete(it) }
    })
    return result.get().modifiedCount
  }

  @Override
  MongoCollection<Document> setupDelete(String dbName, String collectionName) {
    MongoCollection<Document> collection = runWithSpan("setup") {
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
    return collection
  }

  @Override
  int delete(MongoCollection<Document> collection) {
    def result = new CompletableFuture<DeleteResult>()
    def count = new CompletableFuture()
    collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")), toCallback {
      result.complete(it)
      collection.count toCallback { count.complete(it) }
    })
    return result.get().deletedCount
  }

  @Override
  MongoCollection<Document> setupGetMore(String dbName, String collectionName) {
    throw new TestAbortedException("not tested on async")
  }

  @Override
  void getMore(MongoCollection<Document> collection) {
    throw new TestAbortedException("not tested on async")
  }

  @Override
  void error(String dbName, String collectionName) {
    MongoCollection<Document> collection = runWithSpan("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def latch = new CountDownLatch(1)
      db.createCollection(collectionName, toCallback {
        latch.countDown()
      })
      latch.await()
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)
    def result = new CompletableFuture<Throwable>()
    collection.updateOne(new BsonDocument(), new BsonDocument(), toCallback {
      result.complete(it)
    })
    throw result.join()
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
}
