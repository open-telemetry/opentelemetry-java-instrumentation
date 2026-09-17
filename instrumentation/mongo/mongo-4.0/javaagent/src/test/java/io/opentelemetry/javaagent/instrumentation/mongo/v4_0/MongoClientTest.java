/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.connection.ClusterId;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest;
import io.opentelemetry.instrumentation.mongo.testing.ClusterIdCapture;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MongoClientTest extends AbstractMongoClientTest<MongoCollection<Document>> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private MongoClient client;

  @BeforeAll
  void setup() throws ReflectiveOperationException {
    MongoClientSettings.Builder settings =
        MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString("mongodb://" + host + ":" + port));
    if (testLatestDeps()) {
      applyNettyTransport(settings);
    }
    client = MongoClients.create(settings.build());
    cleanup.deferAfterAll(client);
  }

  private static void applyNettyTransport(MongoClientSettings.Builder settings)
      throws ReflectiveOperationException {
    Class<?> transportSettingsClass = Class.forName("com.mongodb.connection.TransportSettings");
    Object nettySettingsBuilder = transportSettingsClass.getMethod("nettyBuilder").invoke(null);
    Object nettySettings =
        nettySettingsBuilder.getClass().getMethod("build").invoke(nettySettingsBuilder);
    MongoClientSettings.Builder.class
        .getMethod("transportSettings", transportSettingsClass)
        .invoke(settings, nettySettings);
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean supportsNetworkPeer() {
    return true;
  }

  @Override
  protected void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected void createCollectionNoDescription(String dbName, String collectionName) {
    MongoClient mongoClient = MongoClients.create("mongodb://" + host + ":" + port);
    cleanup.deferCleanup(mongoClient);
    mongoClient.getDatabase(dbName).createCollection(collectionName);
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
    MongoClient mongoClient = MongoClients.create(settings.build());
    cleanup.deferCleanup(mongoClient);
    mongoClient.getDatabase(dbName).createCollection(collectionName);
  }

  @Test
  void commandUsesTheClusterIdentityFromClientConstruction() {
    ClusterIdCapture clusterIdCapture = new ClusterIdCapture();
    AtomicReference<ClusterId> commandClusterId = new AtomicReference<>();
    CommandListener commandListener =
        new CommandListener() {
          @Override
          public void commandStarted(CommandStartedEvent event) {
            commandClusterId.set(
                event.getConnectionDescription().getConnectionId().getServerId().getClusterId());
          }

          @Override
          public void commandSucceeded(CommandSucceededEvent event) {}

          @Override
          public void commandFailed(CommandFailedEvent event) {}
        };
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder ->
                    builder
                        .hosts(singletonList(new ServerAddress(host, port)))
                        .addClusterListener(clusterIdCapture))
            .addCommandListener(commandListener)
            .build();
    MongoClient mongoClient = MongoClients.create(settings);
    cleanup.deferCleanup(mongoClient);

    mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));

    assertThat(commandClusterId.get()).isSameAs(clusterIdCapture.getClusterId());
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
