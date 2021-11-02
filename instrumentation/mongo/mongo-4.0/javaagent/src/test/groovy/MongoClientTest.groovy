/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.mongodb.MongoClientSettings
import com.mongodb.ServerAddress
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import org.opentest4j.TestAbortedException
import spock.lang.Shared

class MongoClientTest extends AbstractMongoClientTest<MongoCollection<Document>> implements AgentTestTrait {

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
    db.createCollection(collectionName)
  }

  @Override
  void createCollectionNoDescription(String dbName, String collectionName) {
    MongoDatabase db = MongoClients.create("mongodb://localhost:${port}").getDatabase(dbName)
    db.createCollection(collectionName)
  }

  @Override
  void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName) {
    throw new TestAbortedException("not tested on 4.0")
  }

  @Override
  void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    def settings = MongoClientSettings.builder()
      .applyToClusterSettings({ builder ->
        builder.hosts(Arrays.asList(
          new ServerAddress("localhost", port)))
      })
    settings.build()
    MongoDatabase db = MongoClients.create(settings.build()).getDatabase(dbName)
    db.createCollection(collectionName)
  }

  @Override
  int getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    return db.getCollection(collectionName).estimatedDocumentCount()
  }

  @Override
  MongoCollection<Document> setupInsert(String dbName, String collectionName) {
    MongoCollection<Document> collection = runWithSpan("setup") {
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
    return collection.estimatedDocumentCount()
  }

  @Override
  MongoCollection<Document> setupUpdate(String dbName, String collectionName) {
    MongoCollection<Document> collection = runWithSpan("setup") {
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
    collection.estimatedDocumentCount()
    return result.modifiedCount
  }

  @Override
  MongoCollection<Document> setupDelete(String dbName, String collectionName) {
    MongoCollection<Document> collection = runWithSpan("setup") {
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
    collection.estimatedDocumentCount()
    return result.deletedCount
  }

  @Override
  MongoCollection<Document> setupGetMore(String dbName, String collectionName) {
    MongoCollection<Document> collection = runWithSpan("setup") {
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
    MongoCollection<Document> collection = runWithSpan("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)
    collection.updateOne(new BsonDocument(), new BsonDocument())
  }
}
