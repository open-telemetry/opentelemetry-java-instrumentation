/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.testing;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@TestInstance(PER_CLASS)
public abstract class AbstractMongoClientTest<T> {

  private static final AtomicInteger collectionIndex = new AtomicInteger();

  private GenericContainer<?> mongodb;
  protected String host;
  protected int port;

  @BeforeAll
  void setup() {
    mongodb =
        new GenericContainer<>("mongo:4.0")
            .withExposedPorts(27017)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("mongodb")));
    mongodb.start();
    host = mongodb.getHost();
    port = mongodb.getMappedPort(27017);
  }

  @AfterAll
  void cleanup() {
    if (mongodb != null) {
      mongodb.stop();
    }
  }

  protected abstract InstrumentationExtension testing();

  // Different client versions have different APIs to do these operations. If adding a test for a
  // new
  // version, refer to existing ones on how to implement these operations.
  protected abstract void createCollection(String dbName, String collectionName);

  protected abstract void createCollectionNoDescription(String dbName, String collectionName)
      throws InterruptedException;

  // Tests the fix for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/457
  // TracingCommandListener might get added multiple times if clientOptions are built using existing
  // clientOptions or when calling a build method twice.
  // This test asserts that duplicate traces are not created in those cases.
  protected abstract void createCollectionWithAlreadyBuiltClientOptions(
      String dbName, String collectionName);

  protected abstract void createCollectionCallingBuildTwice(String dbName, String collectionName)
      throws InterruptedException;

  protected abstract int getCollection(String dbName, String collectionName) throws Exception;

  protected abstract T setupInsert(String dbName, String collectionName)
      throws InterruptedException;

  protected abstract int insert(T collection) throws Exception;

  protected abstract T setupUpdate(String dbName, String collectionName)
      throws InterruptedException;

  protected abstract int update(T collection) throws Exception;

  protected abstract T setupDelete(String dbName, String collectionName)
      throws InterruptedException;

  protected abstract int delete(T collection) throws Exception;

  protected abstract T setupGetMore(String dbName, String collectionName);

  protected abstract void getMore(T collection);

  protected abstract void error(String dbName, String collectionName) throws Throwable;

  protected void ignoreTracesAndClear(int numberOfTraces) {
    testing().waitForTraces(numberOfTraces);
    testing().clearData();
  }

  @Test
  @DisplayName("test port open")
  void testPortOpen() {
    assertThatNoException().isThrownBy(() -> new Socket(host, port));
  }

  @Test
  @DisplayName("test create collection")
  void testCreateCollection() {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    testing().runWithSpan("parent", () -> createCollection(dbName, collectionName));
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "create",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"create\":\"" + collectionName + "\",\"capped\":\"?\"}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\"}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}"))));
  }

  @Test
  @DisplayName("test create collection no description")
  void testCreateCollectionNoDescription() throws InterruptedException {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    testing().runWithSpan("parent", () -> createCollectionNoDescription(dbName, collectionName));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "create",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"create\":\"" + collectionName + "\",\"capped\":\"?\"}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\"}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}"))));
  }

  @Test
  @DisplayName("test create collection calling build twice")
  void testCreateCollectionCallingBuildTwice() throws InterruptedException {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    testing()
        .runWithSpan("parent", () -> createCollectionCallingBuildTwice(dbName, collectionName));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "create",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"create\":\"" + collectionName + "\",\"capped\":\"?\"}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\"}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}"))));
  }

  @Test
  @DisplayName("test get collection")
  void testGetCollection() throws Exception {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    int count = testing().runWithSpan("parent", () -> getCollection(dbName, collectionName));
    assertThat(count).isEqualTo(0);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "count",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"count\":\"" + collectionName + "\",\"query\":{}}",
                                "{\"count\":\"" + collectionName + "\",\"query\":{},\"$db\":\"?\"}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"query\":{},\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"query\":{},\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}"))));
  }

  @Test
  @DisplayName("test insert")
  void testInsert() throws Exception {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    T collection = setupInsert(dbName, collectionName);
    int count = testing().runWithSpan("parent", () -> insert(collection));

    assertThat(count).isEqualTo(1);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "insert",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"insert\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"documents\":[{\"_id\":\"?\",\"password\":\"?\"}]}",
                                "{\"insert\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"$db\":\"?\",\"documents\":[{\"_id\":\"?\",\"password\":\"?\"}]}",
                                "{\"insert\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"},\"documents\":[{\"_id\":\"?\",\"password\":\"?\"}]}")),
                    span ->
                        mongoSpan(
                            trace,
                            2,
                            "count",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"count\":\"" + collectionName + "\",\"query\":{}}",
                                "{\"count\":\"" + collectionName + "\",\"query\":{},\"$db\":\"?\"}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"query\":{},\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"query\":{},\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}"))));
  }

  @Test
  @DisplayName("test update")
  void testUpdate() throws Exception {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    T collection = setupUpdate(dbName, collectionName);
    int modifiedCount = testing().runWithSpan("parent", () -> update(collection));

    assertThat(modifiedCount).isEqualTo(1);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "update",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"update\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"updates\":[{\"q\":{\"password\":\"?\"},\"u\":{\"$set\":{\"password\":\"?\"}}}]}",
                                "{\"update\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"$db\":\"?\",\"updates\":[{\"q\":{\"password\":\"?\"},\"u\":{\"$set\":{\"password\":\"?\"}}}]}",
                                "{\"update\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"},\"updates\":[{\"q\":{\"password\":\"?\"},\"u\":{\"$set\":{\"password\":\"?\"}}}]}")),
                    span ->
                        mongoSpan(
                            trace,
                            2,
                            "count",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"count\":\"" + collectionName + "\",\"query\":{}}",
                                "{\"count\":\"" + collectionName + "\",\"query\":{},\"$db\":\"?\"}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"query\":{},\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"query\":{},\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}"))));
  }

  @Test
  @DisplayName("test delete")
  void testDelete() throws Exception {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    T collection = setupDelete(dbName, collectionName);
    int deletedCount = testing().runWithSpan("parent", () -> delete(collection));

    assertThat(deletedCount).isEqualTo(1);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "delete",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"delete\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"deletes\":[{\"q\":{\"password\":\"?\"},\"limit\":\"?\"}]}",
                                "{\"delete\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"$db\":\"?\",\"deletes\":[{\"q\":{\"password\":\"?\"},\"limit\":\"?\"}]}",
                                "{\"delete\":\""
                                    + collectionName
                                    + "\",\"ordered\":\"?\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"},\"deletes\":[{\"q\":{\"password\":\"?\"},\"limit\":\"?\"}]}")),
                    span ->
                        mongoSpan(
                            trace,
                            2,
                            "count",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"count\":\"" + collectionName + "\",\"query\":{}}",
                                "{\"count\":\"" + collectionName + "\",\"query\":{},\"$db\":\"?\"}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"query\":{},\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"query\":{},\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"count\":\""
                                    + collectionName
                                    + "\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}"))));
  }

  @Test
  @DisplayName("test collection name for getMore command")
  void testCollectionNameForGetMoreCommand() {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    T collection = setupGetMore(dbName, collectionName);
    testing().runWithSpan("parent", () -> getMore(collection));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "find",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"find\":\""
                                    + collectionName
                                    + "\",\"filter\":{\"_id\":{\"$gte\":\"?\"}},\"batchSize\":\"?\"}",
                                "{\"find\":\""
                                    + collectionName
                                    + "\",\"filter\":{\"_id\":{\"$gte\":\"?\"}},\"batchSize\":\"?\",\"$db\":\"?\"}",
                                "{\"find\":\""
                                    + collectionName
                                    + "\",\"filter\":{\"_id\":{\"$gte\":\"?\"}},\"batchSize\":\"?\",\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"find\":\""
                                    + collectionName
                                    + "\",\"filter\":{\"_id\":{\"$gte\":\"?\"}},\"batchSize\":\"?\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}")),
                    span ->
                        mongoSpan(
                            trace,
                            2,
                            "getMore",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"getMore\":\"?\",\"collection\":\"?\",\"batchSize\":\"?\"}",
                                "{\"getMore\":\"?\",\"collection\":\"?\",\"batchSize\":\"?\",\"$db\":\"?\"}",
                                "{\"getMore\":\"?\",\"collection\":\"?\",\"batchSize\":\"?\",\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}",
                                "{\"getMore\":\"?\",\"collection\":\"?\",\"batchSize\":\"?\",\"$db\":\"?\",\"lsid\":{\"id\":\"?\"}}"))));
  }

  @Test
  @DisplayName("test error")
  void testError() {
    assertThatIllegalArgumentException().isThrownBy(() -> error("test_db", createCollectionName()));
    // Unfortunately not caught by our instrumentation.
    assertThat(testing().spans().size()).isEqualTo(0);
  }

  @Test
  @DisplayName("test create collection with already built ClientOptions")
  void testCreateCollectionWithAlreadyBuiltClientOptions() {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    testing()
        .runWithSpan(
            "parent", () -> createCollectionWithAlreadyBuiltClientOptions(dbName, collectionName));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        mongoSpan(
                            trace,
                            1,
                            "create",
                            collectionName,
                            dbName,
                            trace.getSpan(0),
                            asList(
                                "{\"create\":\"" + collectionName + "\",\"capped\":\"?\"}",
                                "{\"create\":\""
                                    + collectionName
                                    + "\",\"capped\":\"?\",\"$db\":\"?\",\"$readPreference\":{\"mode\":\"?\"}}"))));
  }

  protected String createCollectionName() {
    return "testCollection-" + collectionIndex.getAndIncrement();
  }

  @SuppressWarnings("deprecation")
  // TODO DbIncubatingAttributes.DB_CONNECTION_STRING deprecation
  void mongoSpan(
      TraceAssert trace,
      int index,
      String operation,
      String collection,
      String dbName,
      Object parentSpan,
      List<String> statements) {
    SpanDataAssert span =
        trace.element(index).hasName(operation + " " + dbName + "." + collection).hasKind(CLIENT);
    if (parentSpan == null) {
      span.hasNoParent();
    } else {
      span.hasParent((SpanData) parentSpan);
    }

    span.hasAttributesSatisfyingExactly(
        equalTo(ServerAttributes.SERVER_ADDRESS, host),
        equalTo(ServerAttributes.SERVER_PORT, port),
        satisfies(
            DbIncubatingAttributes.DB_STATEMENT,
            val -> val.matches(statement -> statements.contains(statement.replaceAll(" ", "")))),
        equalTo(DbIncubatingAttributes.DB_SYSTEM, "mongodb"),
        equalTo(DbIncubatingAttributes.DB_CONNECTION_STRING, "mongodb://localhost:" + port),
        equalTo(DbIncubatingAttributes.DB_NAME, dbName),
        equalTo(DbIncubatingAttributes.DB_OPERATION, operation),
        equalTo(DbIncubatingAttributes.DB_MONGODB_COLLECTION, collection));
  }
}
