/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.couchbase.client.core.ClusterFacade;
import com.couchbase.client.java.CouchbaseAsyncBucket;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.query.N1qlQuery;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import rx.Observable;

class CouchbaseQuerySanitizationTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void querySanitizationPreservesDerivedTelemetry() {
    ClusterFacade core = mock(ClusterFacade.class);
    when(core.send(any())).thenReturn(Observable.empty());
    CouchbaseEnvironment environment = mock(CouchbaseEnvironment.class);
    when(environment.queryTimeout()).thenReturn(1000L);
    CouchbaseAsyncBucket bucket =
        new CouchbaseAsyncBucket(core, environment, "test", "", emptyList());
    String query = "SELECT * FROM `test` WHERE field1 = 'secret' AND field2 = \"secret\"";

    assertThat(bucket.query(N1qlQuery.simple(query)).toList().toBlocking().single()).isEmpty();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT `test`")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "couchbase"),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(
                                DB_QUERY_TEXT,
                                Boolean.parseBoolean(
                                        System.getProperty("testQuerySanitizationEnabled", "true"))
                                    ? "SELECT * FROM `test` WHERE field1 = ? AND field2 = ?"
                                    : query),
                            equalTo(DB_QUERY_SUMMARY, "SELECT `test`"))));
  }
}
