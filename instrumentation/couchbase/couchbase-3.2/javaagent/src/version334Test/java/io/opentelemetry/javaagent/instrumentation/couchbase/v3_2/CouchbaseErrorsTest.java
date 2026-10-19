/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.couchbase.client.core.error.ValueTooLargeException;
import com.couchbase.client.core.util.ConnectionString;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.codec.RawBinaryTranscoder;
import com.couchbase.client.java.kv.UpsertOptions;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.couchbase.CouchbaseService;

class CouchbaseErrorsTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static Cluster cluster;
  private static Collection collection;
  private static String seedAddress;
  private static int seedPort;

  @BeforeAll
  static void setup() {
    CouchbaseContainer couchbase =
        new CouchbaseContainer("couchbase/server:7.6.0")
            .withExposedPorts(8091)
            .withEnabledServices(CouchbaseService.KV)
            .withBucket(new BucketDefinition("test"))
            .withStartupTimeout(Duration.ofMinutes(2));
    couchbase.start();
    cleanup.deferAfterAll(couchbase::stop);
    String connectionString = couchbase.getConnectionString();
    ConnectionString.UnresolvedSocket seed =
        ConnectionString.create(connectionString).hosts().get(0);
    seedAddress = seed.hostname();
    seedPort = seed.port();
    cluster = Cluster.connect(connectionString, couchbase.getUsername(), couchbase.getPassword());
    cleanup.deferAfterAll(cluster::disconnect);
    Bucket bucket = cluster.bucket("test");
    collection = bucket.defaultCollection();
    bucket.waitUntilReady(Duration.ofSeconds(30));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void upsertOutcome(boolean oversized) {
    byte[] content = new byte[oversized ? 21 * 1024 * 1024 : 1];
    if (oversized) {
      assertThatThrownBy(
              () ->
                  collection.upsert(
                      "upsert-id",
                      content,
                      UpsertOptions.upsertOptions().transcoder(RawBinaryTranscoder.INSTANCE)))
          .isInstanceOf(ValueTooLargeException.class);
    } else {
      collection.upsert(
          "upsert-id",
          content,
          UpsertOptions.upsertOptions().transcoder(RawBinaryTranscoder.INSTANCE));
    }

    testing.waitAndAssertTracesWithoutScopeVersionVerification(
        trace -> trace.hasSpansSatisfyingExactly(span -> assertUpsertSpan(span, oversized)));
  }

  private static void assertUpsertSpan(SpanDataAssert span, boolean oversized) {
    span.hasName("upsert _default")
        .hasKind(CLIENT)
        .hasNoParent()
        .hasStatus(oversized ? StatusData.error() : StatusData.unset())
        .hasAttributesSatisfyingExactly(
            equalTo(DB_SYSTEM_NAME, "couchbase"),
            equalTo(DB_NAMESPACE, "test"),
            equalTo(DB_OPERATION_NAME, "upsert"),
            equalTo(DB_COLLECTION_NAME, "_default"),
            equalTo(ERROR_TYPE, oversized ? ValueTooLargeException.class.getName() : null),
            equalTo(SERVER_ADDRESS, seedAddress),
            equalTo(SERVER_PORT, seedPort));
  }
}
