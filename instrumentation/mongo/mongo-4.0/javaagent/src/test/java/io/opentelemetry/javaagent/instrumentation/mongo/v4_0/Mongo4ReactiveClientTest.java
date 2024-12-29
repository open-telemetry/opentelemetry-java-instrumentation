/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static java.util.Collections.singletonList;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opentest4j.TestAbortedException;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class Mongo4ReactiveClientTest extends AbstractMongoClientTest<MongoCollection<Document>> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private MongoClient client;

  @BeforeAll
  void setup() {
    client = MongoClients.create("mongodb://" + host + ":" + port);
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
  public void createCollection(String dbName, String collectionName) throws InterruptedException {
    MongoDatabase db = client.getDatabase(dbName);
    CountDownLatch latch = new CountDownLatch(1);
    db.createCollection(collectionName).subscribe(toSubscriber(o -> latch.countDown()));
    latch.await(30, TimeUnit.SECONDS);
  }

  @Override
  public void createCollectionNoDescription(String dbName, String collectionName)
      throws InterruptedException {
    MongoClient tmpClient = MongoClients.create("mongodb://" + host + ":" + port);
    cleanup.deferCleanup(tmpClient);
    MongoDatabase db = tmpClient.getDatabase(dbName);
    CountDownLatch latch = new CountDownLatch(1);
    db.createCollection(collectionName).subscribe(toSubscriber(o -> latch.countDown()));
    latch.await(30, TimeUnit.SECONDS);
  }

  @Override
  public void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName) {
    throw new TestAbortedException("not tested on 4.0");
  }

  @Override
  public void createCollectionCallingBuildTwice(String dbName, String collectionName)
      throws InterruptedException {
    MongoClientSettings.Builder settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder -> builder.hosts(singletonList(new ServerAddress(host, port))));
    settings.build();
    MongoClient tmpClient = MongoClients.create(settings.build());
    cleanup.deferCleanup(tmpClient);
    MongoDatabase db = tmpClient.getDatabase(dbName);
    CountDownLatch latch = new CountDownLatch(1);
    db.createCollection(collectionName).subscribe(toSubscriber(o -> latch.countDown()));
    latch.await(30, TimeUnit.SECONDS);
  }

  @Override
  public long getCollection(String dbName, String collectionName)
      throws ExecutionException, InterruptedException, TimeoutException {
    MongoDatabase db = client.getDatabase(dbName);
    CompletableFuture<Long> count = new CompletableFuture<>();
    db.getCollection(collectionName)
        .estimatedDocumentCount()
        .subscribe(toSubscriber(o -> count.complete(((Long) o))));
    return count.get(30, TimeUnit.SECONDS);
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
                  db.createCollection(collectionName)
                      .subscribe(toSubscriber(o -> latch.countDown()));
                  latch.await(30, TimeUnit.SECONDS);
                  return db.getCollection(collectionName);
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  public long insert(MongoCollection<Document> collection) throws Exception {
    CompletableFuture<Long> count = new CompletableFuture<>();
    collection
        .insertOne(new Document("password", "SECRET"))
        .subscribe(
            toSubscriber(
                result ->
                    collection
                        .estimatedDocumentCount()
                        .subscribe(toSubscriber(c -> count.complete(((Long) c))))));
    return count.get(30, TimeUnit.SECONDS);
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
                  db.createCollection(collectionName)
                      .subscribe(toSubscriber(o -> latch1.countDown()));
                  latch1.await(30, TimeUnit.SECONDS);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  CountDownLatch latch2 = new CountDownLatch(1);
                  coll.insertOne(new Document("password", "OLDPW"))
                      .subscribe(toSubscriber(o -> latch2.countDown()));
                  latch2.await(30, TimeUnit.SECONDS);
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  public long update(MongoCollection<Document> collection) throws Exception {
    CompletableFuture<UpdateResult> result = new CompletableFuture<>();
    CompletableFuture<Long> count = new CompletableFuture<>();
    collection
        .updateOne(
            new BsonDocument("password", new BsonString("OLDPW")),
            new BsonDocument("$set", new BsonDocument("password", new BsonString("NEWPW"))))
        .subscribe(
            toSubscriber(
                updateResult -> {
                  result.complete(((UpdateResult) updateResult));
                  collection
                      .estimatedDocumentCount()
                      .subscribe(toSubscriber(o -> count.complete(((Long) o))));
                }));
    return result.get(30, TimeUnit.SECONDS).getModifiedCount();
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
                  db.createCollection(collectionName)
                      .subscribe(toSubscriber(o -> latch1.countDown()));
                  latch1.await(30, TimeUnit.SECONDS);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  CountDownLatch latch2 = new CountDownLatch(1);
                  coll.insertOne(new Document("password", "SECRET"))
                      .subscribe(toSubscriber(o -> latch2.countDown()));
                  latch2.await(30, TimeUnit.SECONDS);
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  public long delete(MongoCollection<Document> collection)
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<DeleteResult> result = new CompletableFuture<>();
    CompletableFuture<Long> count = new CompletableFuture<>();
    collection
        .deleteOne(new BsonDocument("password", new BsonString("SECRET")))
        .subscribe(
            toSubscriber(
                deleteResult -> {
                  result.complete(((DeleteResult) deleteResult));
                  collection
                      .estimatedDocumentCount()
                      .subscribe(toSubscriber(o -> count.complete(((Long) o))));
                }));
    return result.get(30, TimeUnit.SECONDS).getDeletedCount();
  }

  @Override
  public MongoCollection<Document> setupGetMore(String dbName, String collectionName) {
    throw new TestAbortedException("not tested on reactive");
  }

  @Override
  public void getMore(MongoCollection<Document> collection) {
    throw new TestAbortedException("not tested on reactive");
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
                  db.createCollection(collectionName)
                      .subscribe(toSubscriber(o -> latch.countDown()));
                  latch.await(30, TimeUnit.SECONDS);
                  return db.getCollection(collectionName);
                });
    ignoreTracesAndClear(1);
    CompletableFuture<Throwable> result = new CompletableFuture<>();
    collection
        .updateOne(new BsonDocument(), new BsonDocument())
        .subscribe(toSubscriber(t -> result.complete(((Throwable) t))));
    throw result.get(30, TimeUnit.SECONDS);
  }

  <T> Subscriber<? super T> toSubscriber(Consumer<Object> consumer) {
    return new Subscriber<Object>() {
      private boolean hasResult;

      @Override
      public void onSubscribe(Subscription s) {
        s.request(1); // must request 1 value to trigger async call
      }

      @Override
      public void onNext(Object o) {
        hasResult = true;
        consumer.accept(o);
      }

      @Override
      public void onError(Throwable t) {
        hasResult = true;
        consumer.accept(t);
      }

      @Override
      public void onComplete() {
        if (!hasResult) {
          hasResult = true;
          consumer.accept(null);
        }
      }
    };
  }
}
