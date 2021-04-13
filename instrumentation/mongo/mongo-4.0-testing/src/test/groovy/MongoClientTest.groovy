/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import org.junit.AssumptionViolatedException
import spock.lang.Shared

class MongoClientTest extends AbstractMongoClientTest implements AgentTestTrait {

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
    throw new AssumptionViolatedException("not tested on 4.0")
  }

  @Override
  int getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    return db.getCollection(collectionName).estimatedDocumentCount()
  }

  @Override
  int insert(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      return db.getCollection(collectionName)
    }
    ignoreTracesAndClear(1)
    collection.insertOne(new Document("password", "SECRET"))
    return collection.estimatedDocumentCount()
  }

  @Override
  int update(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      def coll = db.getCollection(collectionName)
      coll.insertOne(new Document("password", "OLDPW"))
      return coll
    }
    ignoreTracesAndClear(1)
    def result = collection.updateOne(
      new BsonDocument("password", new BsonString("OLDPW")),
      new BsonDocument('$set', new BsonDocument("password", new BsonString("NEWPW"))))
    collection.estimatedDocumentCount()
    return result.modifiedCount
  }

  @Override
  int delete(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      db.createCollection(collectionName)
      def coll = db.getCollection(collectionName)
      coll.insertOne(new Document("password", "SECRET"))
      return coll
    }
    ignoreTracesAndClear(1)
    def result = collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")))
    collection.estimatedDocumentCount()
    return result.deletedCount
  }

  @Override
  void getMore(String dbName, String collectionName) {
    MongoCollection<Document> collection = runUnderTrace("setup") {
      MongoDatabase db = client.getDatabase(dbName)
      def coll = db.getCollection(collectionName)
      coll.insertMany([new Document("_id", 0), new Document("_id", 1), new Document("_id", 2)])
      return coll
    }
    ignoreTracesAndClear(1)
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
}
