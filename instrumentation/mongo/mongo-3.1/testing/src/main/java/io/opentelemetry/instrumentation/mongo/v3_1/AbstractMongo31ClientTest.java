/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoTimeoutException;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayList;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractMongo31ClientTest
    extends AbstractMongoClientTest<MongoCollection<Document>> {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected abstract void configureMongoClientOptions(MongoClientOptions.Builder options);

  private MongoClient client;

  @BeforeAll
  void setup() {
    MongoClientOptions.Builder options =
        MongoClientOptions.builder().description("some-description");
    configureMongoClientOptions(options);
    client = new MongoClient(new ServerAddress(host, port), options.build());
    cleanup.deferAfterAll(client);
  }

  @Override
  protected void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected void createCollectionNoDescription(String dbName, String collectionName) {
    MongoClientOptions.Builder options = MongoClientOptions.builder();
    configureMongoClientOptions(options);
    MongoClient mongoClient = new MongoClient(new ServerAddress(host, port), options.build());
    cleanup.deferCleanup(mongoClient);
    MongoDatabase db = mongoClient.getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected void createCollectionWithAlreadyBuiltClientOptions(
      String dbName, String collectionName) {
    MongoClientOptions clientOptions = client.getMongoClientOptions();
    MongoClientOptions newClientOptions = MongoClientOptions.builder(clientOptions).build();
    MongoClient mongoClient = new MongoClient(new ServerAddress(host, port), newClientOptions);
    cleanup.deferCleanup(mongoClient);
    MongoDatabase db = mongoClient.getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    MongoClientOptions.Builder options =
        MongoClientOptions.builder().description("some-description");
    configureMongoClientOptions(options);
    options.build();
    MongoClient mongoClient = new MongoClient(new ServerAddress(host, port), options.build());
    cleanup.deferCleanup(mongoClient);
    MongoDatabase db = mongoClient.getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected long getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    return db.getCollection(collectionName).count();
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
    return collection.count();
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
    collection.count();
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
    collection.count();
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

  @Test
  void schemaValidationFailure() {
    String collectionName = createCollectionName();
    MongoDatabase db = client.getDatabase("test_db");
    db.runCommand(
        new Document("create", collectionName)
            .append("validator", new Document("required", new Document("$exists", true))));
    ignoreTracesAndClear(1);

    assertThatExceptionOfType(MongoWriteException.class)
        .isThrownBy(
            () -> db.getCollection(collectionName).insertOne(new Document("password", "SECRET")))
        .satisfies(error -> assertThat(error.getError().getCode()).isEqualTo(121));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("insert " + collectionName)
                            .hasStatus(StatusData.error())
                            .hasEventsSatisfyingExactly()
                            .hasAttributesSatisfying(
                                equalTo(DB_SYSTEM_NAME, "mongodb"),
                                equalTo(DB_NAMESPACE, "test_db"),
                                equalTo(DB_OPERATION_NAME, "insert"),
                                equalTo(ERROR_TYPE, "121"))));
    assertDurationMetric(
        testing(),
        "io.opentelemetry.mongo-3.1",
        DB_SYSTEM_NAME,
        DB_NAMESPACE,
        DB_COLLECTION_NAME,
        DB_OPERATION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT,
        ERROR_TYPE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"update", "delete"})
  void zeroMatchesAreSuccessful(String operation) {
    MongoCollection<Document> collection = setupInsert("test_db", createCollectionName());
    if (operation.equals("update")) {
      assertThat(
              collection
                  .updateOne(
                      new Document("missing", true),
                      new Document("$set", new Document("password", "SECRET")))
                  .getMatchedCount())
          .isZero();
    } else {
      assertThat(collection.deleteOne(new Document("missing", true)).getDeletedCount()).isZero();
    }
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasStatus(StatusData.unset())
                            .hasAttributesSatisfying(equalTo(ERROR_TYPE, null))));
  }

  @Test
  void testClientFailure() {
    MongoClientOptions options = MongoClientOptions.builder().serverSelectionTimeout(10).build();
    MongoClient client = new MongoClient(new ServerAddress(host, PortUtils.UNUSABLE_PORT), options);
    cleanup.deferCleanup(client);

    assertThatExceptionOfType(MongoTimeoutException.class)
        .isThrownBy(
            () -> {
              MongoDatabase db = client.getDatabase("test_db");
              db.createCollection(createCollectionName());
            });
    // Unfortunately not caught by our instrumentation.
    assertThat(testing().spans()).isEmpty();
  }
}
