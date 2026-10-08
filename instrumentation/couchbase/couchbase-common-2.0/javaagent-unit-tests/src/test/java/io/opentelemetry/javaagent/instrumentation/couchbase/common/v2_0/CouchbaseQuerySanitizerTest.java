/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.couchbase.client.java.analytics.AnalyticsQuery;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.Select;
import com.couchbase.client.java.query.dsl.Expression;
import com.couchbase.client.java.view.SpatialViewQuery;
import com.couchbase.client.java.view.ViewQuery;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CouchbaseQuerySanitizerTest {

  @ParameterizedTest
  @MethodSource("providesArguments")
  void testShouldNormalizeStringQuery(Parameter parameter) {
    String normalized = CouchbaseQuerySanitizer.analyze(parameter.query).getQueryText();
    // the analytics query ends up with trailing ';' in earlier couchbase version, but no trailing
    // ';' in later couchbase version
    assertThat(normalized.replaceFirst(";$", "")).isEqualTo(parameter.expected);
  }

  @Test
  void queryAnalysisIsPreservedInRequestCopies() {
    CouchbaseRequestInfo request =
        CouchbaseRequestInfo.create(
            "test", null, "SELECT field1 FROM `test` WHERE field2 = 'asdf'");
    SqlQuery query = request.getSqlQuery();

    assertThat(query).isNotNull();
    assertThat(query.getQueryText()).isEqualTo("SELECT field1 FROM `test` WHERE field2 = ?");
    assertThat(query.getQuerySummary()).isEqualTo("SELECT `test`");
    assertThat(query.getOperationName()).isEqualTo("SELECT");
    assertThat(query.getCollectionName()).isEqualTo("`test`");
    assertThat(query.getStoredProcedureName()).isNull();
    assertThat(request.getOperation()).isEqualTo("SELECT");

    CouchbaseRequestInfo copy = request.copySupplier().get();
    assertThat(copy).isNotSameAs(request);
    assertThat(copy.getSqlQuery()).isSameAs(query);
    assertThat(copy.getOperation()).isEqualTo("SELECT");
    assertThat(copy.getBucket()).isEqualTo("test");
  }

  private static Stream<Arguments> providesArguments() {
    return Stream.of(
        Arguments.of(
            named(
                "plain string",
                new Parameter(
                    "SELECT field1 FROM `test` WHERE field2 = 'asdf'",
                    "SELECT field1 FROM `test` WHERE field2 = ?"))),
        Arguments.of(
            named(
                "Statement",
                new Parameter(
                    Select.select("field1")
                        .from("test")
                        .where(Expression.path("field2").eq(Expression.s("asdf"))),
                    "SELECT field1 FROM test WHERE field2 = ?"))),
        Arguments.of(
            named(
                "N1QL",
                new Parameter(
                    N1qlQuery.simple("SELECT field1 FROM `test` WHERE field2 = 'asdf'"),
                    "SELECT field1 FROM `test` WHERE field2 = ?"))),
        Arguments.of(
            named(
                "Analytics",
                new Parameter(
                    AnalyticsQuery.simple("SELECT field1 FROM `test` WHERE field2 = 'asdf'"),
                    "SELECT field1 FROM `test` WHERE field2 = ?"))),
        Arguments.of(
            named(
                "View",
                new Parameter(
                    ViewQuery.from("design", "view").skip(10),
                    "ViewQuery(design/view){params=\"skip=10\"}"))),
        Arguments.of(
            named(
                "SpatialView",
                new Parameter(SpatialViewQuery.from("design", "view").skip(10), "skip=10"))));
  }

  private static class Parameter {
    private final Object query;
    private final String expected;

    private Parameter(Object query, String expected) {
      this.query = query;
      this.expected = expected;
    }
  }
}
