/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.couchbase.client.java.view.ViewQuery;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CouchbaseAttributesGetterTest {

  @Test
  void queryCopiesPreserveTelemetry() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "test", null, "SELECT field1 FROM `test` WHERE field2 = 'asdf'");
    CouchbaseSqlAttributesGetter getter = new CouchbaseSqlAttributesGetter();
    AttributesExtractor<CouchbaseRequestInfo, Void> extractor =
        SqlClientAttributesExtractor.create(getter);

    for (CouchbaseRequestInfo copy :
        new CouchbaseRequestInfo[] {request, request.copySupplier().get()}) {
      AttributesBuilder attributes = Attributes.builder();
      extractor.onStart(attributes, Context.root(), copy);

      assertThat(DbClientSpanNameExtractor.create(getter).extract(copy)).isEqualTo("SELECT `test`");
      assertThat(attributes.build().asMap())
          .containsOnly(
              entry(DB_SYSTEM_NAME, "couchbase"),
              entry(DB_NAMESPACE, "test"),
              entry(DB_QUERY_TEXT, "SELECT field1 FROM `test` WHERE field2 = ?"),
              entry(DB_QUERY_SUMMARY, "SELECT `test`"));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void sanitizesSqlStringLiterals(boolean sanitizationEnabled) {
    String query = "SELECT * FROM `test` WHERE field1 = 'secret' AND field2 = \"secret\"";
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("test", null, query);
    CouchbaseSqlAttributesGetter getter = new CouchbaseSqlAttributesGetter();
    AttributesBuilder attributes = Attributes.builder();
    SqlClientAttributesExtractor.builder(getter)
        .setQuerySanitizationEnabled(sanitizationEnabled)
        .build()
        .onStart(attributes, Context.root(), request);

    assertThat(getter.getRawQueryTexts(request)).containsExactly(query);
    assertThat(attributes.build().asMap())
        .containsOnly(
            entry(DB_SYSTEM_NAME, "couchbase"),
            entry(DB_NAMESPACE, "test"),
            entry(
                DB_QUERY_TEXT,
                sanitizationEnabled
                    ? "SELECT * FROM `test` WHERE field1 = ? AND field2 = ?"
                    : query),
            entry(DB_QUERY_SUMMARY, "SELECT `test`"));
  }

  @Test
  void preservesNonSqlOperationNames() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("test", null, getClass(), "get");
    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    AttributesBuilder attributes = Attributes.builder();
    DbClientAttributesExtractor.create(getter).onStart(attributes, Context.root(), request);

    assertThat(request.isSqlQuery()).isFalse();
    assertThat(DbClientSpanNameExtractor.create(getter).extract(request))
        .isEqualTo("CouchbaseAttributesGetterTest.get test");
    assertThat(attributes.build().asMap())
        .containsOnly(
            entry(DB_SYSTEM_NAME, "couchbase"),
            entry(DB_NAMESPACE, "test"),
            entry(DB_OPERATION_NAME, "CouchbaseAttributesGetterTest.get"));
  }

  @Test
  void preservesOpaqueViewQueries() {
    ViewQuery query = ViewQuery.from("design", "view").skip(10);
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("test", null, query);
    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    AttributesBuilder attributes = Attributes.builder();
    DbClientAttributesExtractor.create(getter).onStart(attributes, Context.root(), request);

    assertThat(request.isSqlQuery()).isFalse();
    assertThat(DbClientSpanNameExtractor.create(getter).extract(request)).isEqualTo("test");
    assertThat(attributes.build().asMap())
        .containsOnly(
            entry(DB_SYSTEM_NAME, "couchbase"),
            entry(DB_NAMESPACE, "test"),
            entry(DB_QUERY_TEXT, query.toString()));
  }

  @Test
  void reportsTheConfiguredTargetRatherThanTheNodeThatAnswered() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("cluster.example", -1).build(),
            getClass(),
            "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768));

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerAddress(request)).isEqualTo("cluster.example");
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void omitsTheDefaultPortOfASingleConfiguredSeed() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("node.example", 11210).build(),
            getClass(),
            "get");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void reportsANonDefaultPort() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("node.example", 11211).build(),
            getClass(),
            "get");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerPort(request)).isEqualTo(11211);
  }

  @Test
  void doesNotReportTheNodeThatAnsweredAtStart() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768));

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerAddress(request)).isNull();
    assertThat(getter.getServerPort(request)).isNull();
  }

  @Test
  void doesNotReportTheNodeThatAnsweredAsServerAtEnd() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768));

    AttributesBuilder attributes = Attributes.builder();
    DbClientAttributesExtractor.create(new CouchbaseAttributesGetter())
        .onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().get(SERVER_ADDRESS)).isNull();
    assertThat(attributes.build().get(SERVER_PORT)).isNull();
  }

  @Test
  void preservesTheConfiguredTarget() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("cluster.example", -1).build(),
            getClass(),
            "get");
    request.setNode(new InetSocketAddress("192.0.2.1", 32768));

    AttributesBuilder attributes = Attributes.builder();
    AttributesExtractor<CouchbaseRequestInfo, Void> extractor =
        DbClientAttributesExtractor.create(new CouchbaseAttributesGetter());
    extractor.onStart(attributes, Context.root(), request);
    extractor.onEnd(attributes, Context.root(), request, null, null);

    assertThat(attributes.build().get(SERVER_ADDRESS)).isEqualTo("cluster.example");
    assertThat(attributes.build().get(SERVER_PORT)).isNull();
  }

  @Test
  void carriesNoServerWhenNeitherIsKnown() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getServerAddress(request)).isNull();
    assertThat(getter.getServerPort(request)).isNull();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isNull();
  }

  @Test
  void reportsTheLastContactedPeer() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", null, getClass(), "get");
    InetSocketAddress firstPeer = new InetSocketAddress("192.0.2.1", 32768);
    InetSocketAddress secondPeer = new InetSocketAddress("192.0.2.2", 32769);

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    request.setNode(firstPeer);
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(firstPeer);

    request.setNode(secondPeer);
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(secondPeer);
  }

  @Test
  void keepsTheLastContactedNodeOfEverySubscriptionApart() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("cluster.example", -1).build(),
            getClass(),
            "get");
    InetSocketAddress firstPeer = new InetSocketAddress("192.0.2.1", 32768);
    InetSocketAddress secondPeer = new InetSocketAddress("192.0.2.2", 32769);
    request.setNode(secondPeer);

    CouchbaseRequestInfo copy = request.copySupplier().get();
    copy.setNode(firstPeer);

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(secondPeer);
    assertThat(getter.getNetworkPeerInetSocketAddress(copy, null)).isEqualTo(firstPeer);
  }

  @Test
  void copyCarriesTheConfiguredTargetOfTheClientThatIssuedIt() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "bucket",
            DbServerTarget.builder(11210).addEndpoint("cluster.example", -1).build(),
            getClass(),
            "get");

    CouchbaseRequestInfo copy = request.copySupplier().get();
    assertThat(copy.getBucket()).isEqualTo("bucket");
    assertThat(copy.getOperation()).isEqualTo(request.getOperation());
    assertThat(copy.getServerTarget()).isSameAs(request.getServerTarget());
    assertThat(copy.getNode()).isNull();
  }
}
