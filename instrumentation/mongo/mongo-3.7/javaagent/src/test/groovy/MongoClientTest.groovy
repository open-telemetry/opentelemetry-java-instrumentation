/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import com.mongodb.MongoClientSettings
import com.mongodb.MongoTimeoutException
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
import spock.lang.Shared

import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT

class MongoClientTest extends AbstractMongoClientTest<MongoCollection<Document>> implements AgentTestTrait {

  @Shared
  MongoClient client

  def setupSpec() throws Exception {
    client = MongoClients.create(MongoClientSettings.builder()
      .applyToClusterSettings({ builder ->
        builder.hosts(Arrays.asList(
          new ServerAddress("localhost", port)))
          .description("some-description")
      })
      .build())
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
    def clientSettings = MongoClientSettings.builder()
      .applyToClusterSettings({ builder ->
        builder.hosts(Arrays.asList(
          new ServerAddress("localhost", port)))
          .description("some-description")
      })
      .build()
    def newClientSettings = MongoClientSettings.builder(clientSettings).build()
    MongoDatabase db = MongoClients.create(newClientSettings).getDatabase(dbName)
    db.createCollection(collectionName)
  }

  @Override
  void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    def clientSettings = MongoClientSettings.builder()
      .applyToClusterSettings({ builder ->
        builder.hosts(Arrays.asList(
          new ServerAddress("localhost", port)))
          .description("some-description")
      })
    clientSettings.build()
    MongoDatabase db = MongoClients.create(clientSettings.build()).getDatabase(dbName)
    db.createCollection(collectionName)
  }

  @Override
  int getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName)
    return db.getCollection(collectionName).count()
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
    return collection.count()
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
    collection.count()
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
    collection.count()
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

  def "test client failure"() {
    setup:
    def client = MongoClients.create("mongodb://localhost:" + UNUSABLE_PORT + "/?connectTimeoutMS=10")

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
