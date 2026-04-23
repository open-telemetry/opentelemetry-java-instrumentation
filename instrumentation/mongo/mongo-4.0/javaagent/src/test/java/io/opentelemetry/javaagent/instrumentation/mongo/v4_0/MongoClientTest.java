/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assumptions.abort;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

class MongoClientTest extends AbstractMongoClientTest<MongoCollection<Document>> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private MongoClient client;

  @BeforeAll
  void setup() {
    client = MongoClients.create("mongodb://" + host + ":" + port);
    cleanup.deferAfterAll(client);
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected void createCollectionNoDescription(String dbName, String collectionName) {
    try (MongoClient mongoClient = MongoClients.create("mongodb://" + host + ":" + port)) {
      mongoClient.getDatabase(dbName).createCollection(collectionName);
    }
  }

  @Override
  protected void createCollectionWithAlreadyBuiltClientOptions(
      String dbName, String collectionName) {
    abort("not tested on 4.0");
  }

  @Override
  protected void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    MongoClientSettings.Builder settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder -> builder.hosts(singletonList(new ServerAddress(host, port))));
    settings.build();
    try (MongoClient mongoClient = MongoClients.create(settings.build())) {
      mongoClient.getDatabase(dbName).createCollection(collectionName);
    }
  }

  @Override
  protected long getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    return db.getCollection(collectionName).estimatedDocumentCount();
  }

  @Override
  protected MongoCollection<Document> setupInsert(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  db.createCollection(collectionName);
                  return db.getCollection(collectionName);
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  protected long insert(MongoCollection<Document> collection) {
    collection.insertOne(new Document("password", "SECRET"));
    return collection.estimatedDocumentCount();
  }

  @Override
  protected MongoCollection<Document> setupUpdate(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  db.createCollection(collectionName);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  coll.insertOne(new Document("password", "OLDPW"));
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  protected long update(MongoCollection<Document> collection) {
    UpdateResult result =
        collection.updateOne(
            new BsonDocument("password", new BsonString("OLDPW")),
            new BsonDocument("$set", new BsonDocument("password", new BsonString("NEWPW"))));
    collection.estimatedDocumentCount();
    return result.getModifiedCount();
  }

  @Override
  protected MongoCollection<Document> setupDelete(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  db.createCollection(collectionName);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  coll.insertOne(new Document("password", "SECRET"));
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  protected long delete(MongoCollection<Document> collection) {
    DeleteResult result =
        collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")));
    collection.estimatedDocumentCount();
    return result.getDeletedCount();
  }

  @Override
  protected MongoCollection<Document> setupGetMore(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  coll.insertMany(
                      asList(
                          new Document("_id", 0), new Document("_id", 1), new Document("_id", 2)));
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  protected void getMore(MongoCollection<Document> collection) {
    collection
        .find()
        .filter(new Document("_id", new Document("$gte", 0)))
        .batchSize(2)
        .into(new ArrayList<>());
  }

  @Override
  protected void error(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  db.createCollection(collectionName);
                  return db.getCollection(collectionName);
                });
    ignoreTracesAndClear(1);
    collection.updateOne(new BsonDocument(), new BsonDocument());
  }
}
