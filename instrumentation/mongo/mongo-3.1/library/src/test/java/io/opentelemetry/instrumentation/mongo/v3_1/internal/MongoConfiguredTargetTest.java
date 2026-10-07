/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

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
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
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
  void configuredSeedGroupIsReportedAsOneLogicalServer() {
    ClusterId clusterId = new ClusterId();
    MongoClusterTargets.register(
        clusterId,
        MongoServerTarget.seeds(asList(SELECTED_SERVER, new ServerAddress("db1.example", 27017))));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event)).isEqualTo("db1.example:27017,db2.example:27018");
    assertThat(getter.getServerPort(event)).isEqualTo(null);
  }

  @Test
  void configuredSingleDefaultSeedOmitsItsPort() {
    ClusterId clusterId =
        configuredCluster(
            MongoServerTarget.seeds(singletonList(new ServerAddress("db1.example", 27017))));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event)).isEqualTo("db1.example");
    assertThat(getter.getServerPort(event)).isEqualTo(null);
    assertThat(getter.getNetworkPeerAddress(event, null)).isNull();
    assertThat(getter.getNetworkPeerPort(event, null)).isNull();
  }

  @Test
  void configuredSingleCustomSeedReportsTheConfiguredPort() {
    ClusterId clusterId =
        configuredCluster(
            MongoServerTarget.seeds(singletonList(new ServerAddress("db1.example", 28017))));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event)).isEqualTo("db1.example");
    assertThat(getter.getServerPort(event)).isEqualTo(28017);
  }

  @Test
  void configuredTargetIsStableAcrossSelectedServers() {
    ClusterId clusterId =
        configuredCluster(
            MongoServerTarget.seeds(
                asList(
                    new ServerAddress("db2.example", 27017),
                    new ServerAddress("db1.example", 27017))));
    CommandStartedEvent first = commandStartedEvent(clusterId, "test_db", "find");
    CommandStartedEvent second =
        commandStartedEvent(clusterId, new ServerAddress("db3.example", 27019), "test_db", "find");

    assertThat(getter.getServerAddress(first)).isEqualTo("db1.example,db2.example");
    assertThat(getter.getServerPort(first)).isEqualTo(null);
    assertThat(getter.getServerAddress(second)).isEqualTo("db1.example,db2.example");
    assertThat(getter.getServerPort(second)).isEqualTo(null);
  }

  @Test
  void configuredSrvHostDescribesEveryCommand() {
    ClusterId clusterId = configuredCluster(MongoServerTarget.srvHost("cluster0.example.com"));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event)).isEqualTo("mongodb+srv://cluster0.example.com");
    assertThat(getter.getServerPort(event)).isEqualTo(null);
  }

  @Test
  void existingNoArgListenerDoesNotInferAConfiguredTarget() {
    CommandStartedEvent event = commandStartedEvent(new ClusterId(), "test_db", "find");
    CommandListener listener = MongoTelemetry.create(OpenTelemetry.noop()).createCommandListener();

    listener.commandStarted(event);

    Attributes attributes = extractAttributes(event);

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo(null);
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(null);
    assertThat(attributes.get(NETWORK_PEER_ADDRESS)).isNull();
    assertThat(attributes.get(NETWORK_PEER_PORT)).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // db.connection_string is part of the old semantic conventions
  void explicitSingleServerListenerRegistersTheConfiguredTarget() {
    ClusterId clusterId = new ClusterId();
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");
    CommandListener listener =
        MongoTelemetry.create(OpenTelemetry.noop())
            .createCommandListener(singletonList(new ServerAddress("configured.example", 27017)));

    listener.commandStarted(event);

    Attributes attributes = extractAttributes(event);

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo("configured.example");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(null);
    assertThat(attributes.get(NETWORK_PEER_ADDRESS)).isNull();
    assertThat(attributes.get(NETWORK_PEER_PORT)).isNull();
    assertThat(attributes.get(DB_CONNECTION_STRING)).isEqualTo(null);
  }

  @Test
  void explicitSeedListListenerRegistersTheConfiguredTarget() {
    ClusterId clusterId = new ClusterId();
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");
    CommandListener listener =
        MongoTelemetry.create(OpenTelemetry.noop())
            .createCommandListener(
                asList(
                    new ServerAddress("configured2.example", 27018),
                    new ServerAddress("configured1.example", 27017)));

    listener.commandStarted(event);

    Attributes attributes = extractAttributes(event);

    assertThat(attributes.get(SERVER_ADDRESS))
        .isEqualTo("configured1.example:27017,configured2.example:27018");
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(null);
    assertThat(attributes.get(NETWORK_PEER_ADDRESS)).isNull();
    assertThat(attributes.get(NETWORK_PEER_PORT)).isNull();
  }

  @Test
  void mixedUnixSocketAndTcpListenerOmitsTheConfiguredTarget() {
    ClusterId clusterId = new ClusterId();
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");
    CommandListener listener =
        MongoTelemetry.create(OpenTelemetry.noop())
            .createCommandListener(
                asList(
                    new ServerAddress("/tmp/mongodb-27017.sock"),
                    new ServerAddress("configured.example", 27018)));

    listener.commandStarted(event);

    Attributes attributes = extractAttributes(event);

    assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo(null);
    assertThat(attributes.get(SERVER_PORT)).isEqualTo(null);
    assertThat(attributes.get(NETWORK_PEER_ADDRESS)).isNull();
    assertThat(attributes.get(NETWORK_PEER_PORT)).isNull();
  }

  @Test
  void commandWithNoDatabaseUsesConfiguredSeedsInSpanName() {
    ClusterId clusterId = new ClusterId();
    MongoClusterTargets.register(
        clusterId,
        MongoServerTarget.seeds(asList(SELECTED_SERVER, new ServerAddress("db1.example", 27017))));
    CommandStartedEvent event = commandStartedEvent(clusterId, null, "listDatabases");

    String spanName = DbClientSpanNameExtractor.create(getter).extract(event);

    assertThat(spanName).isEqualTo("listDatabases db1.example:27017,db2.example:27018");
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
    return commandStartedEvent(clusterId, SELECTED_SERVER, databaseName, commandName);
  }

  private static CommandStartedEvent commandStartedEvent(
      ClusterId clusterId, ServerAddress selectedServer, String databaseName, String commandName) {
    ConnectionDescription connectionDescription =
        new ConnectionDescription(new ServerId(clusterId, selectedServer));
    return new CommandStartedEvent(
        0,
        connectionDescription,
        databaseName,
        commandName,
        new BsonDocument(commandName, new BsonInt32(1)));
  }
}
