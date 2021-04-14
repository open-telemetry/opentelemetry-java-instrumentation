/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoTimeoutException
import com.mongodb.ServerAddress
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest
import io.opentelemetry.instrumentation.test.utils.PortUtils
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import spock.lang.Shared

abstract class AbstractMongo31ClientTest extends AbstractMongoClientTest<MongoCollection<Document>> {

  abstract void configureMongoClientOptions(MongoClientOptions.Builder options);

  @Shared
  MongoClient client

  def setupSpec() throws Exception {
    def options = MongoClientOptions.builder().description("some-description")
    configureMongoClientOptions(options)
    client = new MongoClient(new ServerAddress("localhost", port), options.build())
  }

  def cleanupSpec() throws Exception {
    client?.close()
    client = null
  }

  @Override
  void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    db.createCollection(collectionName)
  }

  @Override
  void createCollectionNoDescription(String dbName, String collectionName) {
    def options = MongoClientOptions.builder()
    configureMongoClientOptions(options)
    MongoDatabase db = new MongoClient(new ServerAddress("localhost", port), options.build()).getDatabase(dbName)
    db.createCollection(collectionName)
  }

  @Override
  void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName) {
    def clientOptions = client.mongoClientOptions
    def newClientOptions = MongoClientOptions.builder(clientOptions).build()
    MongoDatabase db = new MongoClient(new ServerAddress("localhost", port), newClientOptions).getDatabase(dbName)
    db.createCollection(collectionName)
  }

  @Override
  void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    def options = MongoClientOptions.builder().description("some-description")
    configureMongoClientOptions(options)
    options.build()
    MongoDatabase db = new MongoClient(new ServerAddress("localhost", port), options.build()).getDatabase(dbName)
    db.createCollection(collectionName)
  }

  @Override
  int getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    return db.getCollection(collectionName).count()
  }

  @Override
  MongoCollection<Document> setupInsert(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)
    return collection
  }

  @Override
  int insert(MongoCollection<Document> collection) {
    collection.insertOne(new Document("password", "SECRET"))
    return collection.count()
  }

  @Override
  MongoCollection<Document> setupUpdate(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      def coll = db.getCollection(collectionName)
      coll.insertOne(new Document("password", "OLDPW"))
      return coll
    }
    ignoreTracesAndClear(1)
    return collection
  }

  @Override
  int update(MongoCollection<Document> collection) {
    def result = collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW"))))
    collection.count()
    return result.modifiedCount
  }

  @Override
  MongoCollection<Document> setupDelete(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      def coll = db.getCollection(collectionName)
      coll.insertOne(new Document("password", "SECRET"))
      return coll
    }
    ignoreTracesAndClear(1)
    return collection
  }

  @Override
  int delete(MongoCollection<Document> collection) {
    def result = collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")))
    collection.count()
    return result.deletedCount
  }

  @Override
  MongoCollection<Document> setupGetMore(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def coll = db.getCollection(collectionName)
      coll.insertMany([new Document("_id", 0), new Document("_id", 1), new Document("_id", 2)])
      return coll
    }
    ignoreTracesAndClear(1)
    return collection
  }

  @Override
  void getMore(MongoCollection<Document> collection) {
    collection.find().filter(new Document("_id", new Document('$gte', 0)))
      .batchSize(2).into(new ArrayList())
  }

  @Override
  void error(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)
    collection.updateOne(new BsonDocument(), new BsonDocument())
  }

  def "test client failure"() {
    setup:
    def options = MongoClientOptions.builder().serverSelectionTimeout(10).build()
    def client = new MongoClient(new ServerAddress("localhost", PortUtils.UNUSABLE_PORT), [], options)

    when:
    MongoDatabase db = client.getDatabase(dbName)
    db.createCollection(collectionName)

    then:
    thrown(MongoTimeoutException)
    // Unfortunately not caught by our instrumentation.
    assertTraces(0) {}

    where:
    dbName = "test_db"
    collectionName = createCollectionName()
  }
}
