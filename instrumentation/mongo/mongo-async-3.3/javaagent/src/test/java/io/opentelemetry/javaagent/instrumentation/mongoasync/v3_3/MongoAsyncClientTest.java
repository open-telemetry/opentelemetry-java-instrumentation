/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongoasync.v3_3;

import com.mongodb.ConnectionString;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoClients;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opentest4j.TestAbortedException;

class MongoAsyncClientTest extends AbstractMongoClientTest<MongoCollection<Document>> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private MongoClient client;

  @BeforeAll
  void setup() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .clusterSettings(
                    ClusterSettings.builder()
                        .description("some-description")
                        .applyConnectionString(
                            new ConnectionString("mongodb://" + host + ":" + port))
                        .build())
                .build());
  }

  @AfterAll
  void cleanup() {
    if (client != null) {
      client.close();
    }
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    db.createCollection(collectionName, toCallback(result -> {}));
  }

  @Override
  public void createCollectionNoDescription(String dbName, String collectionName) {
    MongoDatabase db = MongoClients.create("mongodb://" + host + ":" + port).getDatabase(dbName);
    db.createCollection(collectionName, toCallback(result -> {}));
  }

  @Override
  public void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName) {
    MongoClientSettings clientSettings = client.getSettings();
    MongoClientSettings newClientSettings = MongoClientSettings.builder(clientSettings).build();
    MongoDatabase db = MongoClients.create(newClientSettings).getDatabase(dbName);
    db.createCollection(collectionName, toCallback(result -> {}));
  }

  @Override
  public void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    MongoClientSettings.Builder settings =
        MongoClientSettings.builder()
            .clusterSettings(
                ClusterSettings.builder()
                    .description("some-description")
                    .applyConnectionString(new ConnectionString("mongodb://" + host + ":" + port))
                    .build());
    settings.build();
    MongoDatabase db = MongoClients.create(settings.build()).getDatabase(dbName);
    db.createCollection(collectionName, toCallback(result -> {}));
  }

  @Override
  public long getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    CompletableFuture<Long> count = new CompletableFuture<>();
    db.getCollection(collectionName).count(toCallback(o -> count.complete(((Long) o))));
    return count.join();
  }

  @Override
  public MongoCollection<Document> setupInsert(String dbName, String collectionName)
      throws InterruptedException {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  CountDownLatch latch = new CountDownLatch(1);
                  db.createCollection(collectionName, toCallback(result -> latch.countDown()));
                  latch.await(30, TimeUnit.SECONDS);
                  return db.getCollection(collectionName);
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  public long insert(MongoCollection<Document> collection) {
    CompletableFuture<Long> count = new CompletableFuture<>();
    collection.insertOne(
        new Document("password", "SECRET"),
        toCallback(result -> collection.count(toCallback(o -> count.complete(((Long) o))))));
    return count.join();
  }

  @Override
  public MongoCollection<Document> setupUpdate(String dbName, String collectionName)
      throws InterruptedException {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  CountDownLatch latch1 = new CountDownLatch(1);
                  db.createCollection(collectionName, toCallback(result -> latch1.countDown()));
                  latch1.await(30, TimeUnit.SECONDS);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  CountDownLatch latch2 = new CountDownLatch(1);
                  coll.insertOne(
                      new Document("password", "OLDPW"), toCallback(result -> latch2.countDown()));
                  latch2.await(30, TimeUnit.SECONDS);
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  public long update(MongoCollection<Document> collection) {
    CompletableFuture<UpdateResult> result = new CompletableFuture<>();
    CompletableFuture<Long> count = new CompletableFuture<>();
    collection.updateOne(
        new BsonDocument("password", new BsonString("OLDPW")),
        new BsonDocument("$set", new BsonDocument("password", new BsonString("NEWPW"))),
        toCallback(
            res -> {
              result.complete(((UpdateResult) res));
              collection.count(toCallback(o -> count.complete(((Long) o))));
            }));
    return result.join().getModifiedCount();
  }

  @Override
  public MongoCollection<Document> setupDelete(String dbName, String collectionName)
      throws InterruptedException {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  CountDownLatch latch1 = new CountDownLatch(1);
                  db.createCollection(collectionName, toCallback(result -> latch1.countDown()));
                  latch1.await(30, TimeUnit.SECONDS);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  CountDownLatch latch2 = new CountDownLatch(1);
                  coll.insertOne(
                      new Document("password", "SECRET"), toCallback(result -> latch2.countDown()));
                  latch2.await(30, TimeUnit.SECONDS);
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  public long delete(MongoCollection<Document> collection) {
    CompletableFuture<DeleteResult> result = new CompletableFuture<>();
    CompletableFuture<Long> count = new CompletableFuture<>();
    collection.deleteOne(
        new BsonDocument("password", new BsonString("SECRET")),
        toCallback(
            res -> {
              result.complete((DeleteResult) res);
              collection.count(toCallback(value -> count.complete(((Long) value))));
            }));
    return result.join().getDeletedCount();
  }

  @Override
  public MongoCollection<Document> setupGetMore(String dbName, String collectionName) {
    throw new TestAbortedException("not tested on async");
  }

  @Override
  public void getMore(MongoCollection<Document> collection) {
    throw new TestAbortedException("not tested on async");
  }

  @Override
  public void error(String dbName, String collectionName) throws Throwable {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  CountDownLatch latch = new CountDownLatch(1);
                  db.createCollection(collectionName, toCallback(result -> latch.countDown()));
                  latch.await(30, TimeUnit.SECONDS);
                  return db.getCollection(collectionName);
                });
    ignoreTracesAndClear(1);
    CompletableFuture<Throwable> result = new CompletableFuture<>();
    collection.updateOne(
        new BsonDocument(),
        new BsonDocument(),
        toCallback(res -> result.complete((Throwable) res)));
    throw result.join();
  }

  <T> SingleResultCallback<T> toCallback(Consumer<Object> closure) {
    return (result, t) -> {
      if (t != null) {
        closure.accept(t);
      } else {
        closure.accept(result);
      }
    };
  }
}
