/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class MongoConfiguredTargetTest {

  private static final ServerAddress SELECTED_SERVER = new ServerAddress("db2.example", 27018);

  private final MongoDbAttributesGetter getter =
      new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

  @Test
  void configuredSeedGroupIsReportedAsOneStableLogicalServer() {
    ClusterId clusterId = new ClusterId();
    MongoClusterTargets.register(
        clusterId,
        MongoServerTarget.seeds(asList(new ServerAddress("db1.example", 27017), SELECTED_SERVER)));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event))
        .isEqualTo(
            emitStableDatabaseSemconv() ? "db1.example:27017,db2.example:27018" : "db2.example");
    assertThat(getter.getServerPort(event)).isEqualTo(emitStableDatabaseSemconv() ? null : 27018);
  }

  @Test
  void configuredSingleSeedKeepsItsPort() {
    ClusterId clusterId =
        configuredCluster(
            MongoServerTarget.seeds(singletonList(new ServerAddress("db1.example", 27017))));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event))
        .isEqualTo(emitStableDatabaseSemconv() ? "db1.example" : "db2.example");
    assertThat(getter.getServerPort(event)).isEqualTo(emitStableDatabaseSemconv() ? 27017 : 27018);
    assertThat(getter.getNetworkPeerAddress(event, null)).isNull();
    assertThat(getter.getNetworkPeerPort(event, null)).isNull();
  }

  @Test
  void configuredSrvHostDescribesEveryCommand() {
    ClusterId clusterId = configuredCluster(MongoServerTarget.srvHost("cluster0.example.com"));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event))
        .isEqualTo(
            emitStableDatabaseSemconv() ? "mongodb+srv://cluster0.example.com" : "db2.example");
    assertThat(getter.getServerPort(event)).isEqualTo(emitStableDatabaseSemconv() ? null : 27018);
  }

  @Test
  void existingNoArgListenerDoesNotInferAStableTarget() {
    CommandStartedEvent event = commandStartedEvent(new ClusterId(), "test_db", "find");
    CommandListener listener = MongoTelemetry.create(OpenTelemetry.noop()).createCommandListener();

    listener.commandStarted(event);

    Attributes attributes = extractAttributes(event);

    assertThat(attributes.get(SERVER_ADDRESS))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "db2.example");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(emitStableDatabaseSemconv() ? null : 27018L);
    assertThat(attributes.get(NETWORK_PEER_ADDRESS)).isNull();
    assertThat(attributes.get(NETWORK_PEER_PORT)).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // db.connection_string is part of the old semantic conventions
  void explicitSingleServerListenerRegistersTheStableTarget() {
    ClusterId clusterId = new ClusterId();
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");
    CommandListener listener =
        MongoTelemetry.create(OpenTelemetry.noop())
            .createCommandListener(new ServerAddress("configured.example", 27017));

    listener.commandStarted(event);

    Attributes attributes = extractAttributes(event);

    assertThat(attributes.get(SERVER_ADDRESS))
        .isEqualTo(emitStableDatabaseSemconv() ? "configured.example" : "db2.example");
    assertThat(attributes.get(SERVER_PORT))
        .isEqualTo(emitStableDatabaseSemconv() ? 27017L : 27018L);
    assertThat(attributes.get(NETWORK_PEER_ADDRESS)).isNull();
    assertThat(attributes.get(NETWORK_PEER_PORT)).isNull();
    assertThat(attributes.get(DB_CONNECTION_STRING))
        .isEqualTo(emitOldDatabaseSemconv() ? "mongodb://db2.example:27018" : null);
  }

  @Test
  void explicitSeedListListenerRegistersTheStableTarget() {
    ClusterId clusterId = new ClusterId();
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");
    CommandListener listener =
        MongoTelemetry.create(OpenTelemetry.noop())
            .createCommandListener(
                asList(
                    new ServerAddress("configured1.example", 27017),
                    new ServerAddress("configured2.example", 27018)));

    listener.commandStarted(event);

    Attributes attributes = extractAttributes(event);

    assertThat(attributes.get(SERVER_ADDRESS))
        .isEqualTo(
            emitStableDatabaseSemconv()
                ? "configured1.example:27017,configured2.example:27018"
                : "db2.example");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(emitStableDatabaseSemconv() ? null : 27018L);
    assertThat(attributes.get(NETWORK_PEER_ADDRESS)).isNull();
    assertThat(attributes.get(NETWORK_PEER_PORT)).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // db.connection_string is part of the old semantic conventions
  void theOldConnectionStringKeepsDescribingTheServerThatAnswered() {
    ClusterId clusterId =
        configuredCluster(
            MongoServerTarget.seeds(singletonList(new ServerAddress("db1.example", 27017))));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getConnectionString(event)).isEqualTo("mongodb://db2.example:27018");
  }

  @Test
  void commandWithNoDatabaseUsesConfiguredSeedsInStableSpanName() {
    ClusterId clusterId = new ClusterId();
    MongoClusterTargets.register(
        clusterId,
        MongoServerTarget.seeds(asList(new ServerAddress("db1.example", 27017), SELECTED_SERVER)));
    CommandStartedEvent event = commandStartedEvent(clusterId, null, "listDatabases");

    String spanName = new MongoSpanNameExtractor(getter).extract(event);

    assertThat(spanName)
        .isEqualTo(
            emitStableDatabaseSemconv()
                ? "listDatabases db1.example:27017,db2.example:27018"
                : "listDatabases");
  }

  private Attributes extractAttributes(CommandStartedEvent event) {
    AttributesBuilder attributes = Attributes.builder();
    AttributesExtractor<CommandStartedEvent, Void> extractor =
        DbClientAttributesExtractor.create(getter);
    extractor.onStart(attributes, Context.root(), event);
    extractor.onEnd(attributes, Context.root(), event, null, null);
    return attributes.build();
  }

  private static ClusterId configuredCluster(MongoServerTarget target) {
    ClusterId clusterId = new ClusterId();
    MongoClusterTargets.register(clusterId, target);
    return clusterId;
  }

  private static CommandStartedEvent commandStartedEvent(
      ClusterId clusterId, String databaseName, String commandName) {
    ConnectionDescription connectionDescription =
        new ConnectionDescription(new ServerId(clusterId, SELECTED_SERVER));
    return new CommandStartedEvent(
        0,
        connectionDescription,
        databaseName,
        commandName,
        new BsonDocument(commandName, new BsonInt32(1)));
  }
}
