/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TracingCommandListenerTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("responses")
  void terminalWriteResult(String response, String errorType) {
    TracingCommandListener listener =
        new TracingCommandListener(
            MongoInstrumenterFactory.createInstrumenter(
                testing.getOpenTelemetry(), "test-mongo", true));
    ConnectionDescription connection =
        new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));
    listener.commandStarted(
        new CommandStartedEvent(
            1, connection, "test", "update", BsonDocument.parse("{update: 'items'}")));
    listener.commandSucceeded(
        new CommandSucceededEvent(1, connection, "update", BsonDocument.parse(response), 1));
    // Duplicate completion must not create another span or duration observation.
    listener.commandSucceeded(
        new CommandSucceededEvent(1, connection, "update", BsonDocument.parse(response), 1));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("update items")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(
                            StatusData.create(
                                errorType == null ? StatusCode.UNSET : StatusCode.ERROR, ""))
                        .hasEventsSatisfyingExactly()
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "mongodb"),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(DB_COLLECTION_NAME, "items"),
                            equalTo(DB_OPERATION_NAME, "update"),
                            satisfies(
                                DB_QUERY_TEXT,
                                val -> val.matches("\\{\\s*\"update\"\\s*:\\s*\"items\"\\s*}")),
                            equalTo(ERROR_TYPE, errorType))));
    assertDurationMetric(
        testing,
        "test-mongo",
        errorType == null
            ? new AttributeKey<?>[] {
              DB_SYSTEM_NAME, DB_NAMESPACE, DB_COLLECTION_NAME, DB_OPERATION_NAME
            }
            : new AttributeKey<?>[] {
              DB_SYSTEM_NAME, DB_NAMESPACE, DB_COLLECTION_NAME, DB_OPERATION_NAME, ERROR_TYPE
            });
    testing.waitAndAssertMetrics(
        "test-mongo",
        "db.client.operation.duration",
        metrics ->
            metrics
                .singleElement()
                .satisfies(
                    metric ->
                        assertThat(metric.getHistogramData().getPoints())
                            .singleElement()
                            .satisfies(
                                point -> {
                                  assertThat(point.getCount()).isEqualTo(1);
                                  assertThat(point.getAttributes().get(ERROR_TYPE))
                                      .isEqualTo(errorType);
                                })));
  }

  private static Stream<Arguments> responses() {
    return Stream.of(
        Arguments.of(
            "{ok: 1, n: 0, writeErrors: [{index: 0, code: 121, errmsg: 'private value'}]}", "121"),
        Arguments.of("{ok: 1, n: 1, writeConcernError: {code: 64, errmsg: 'private value'}}", "64"),
        Arguments.of("{ok: 1, writeErrors: [{code: 121}], writeConcernError: {code: 64}}", "121"),
        Arguments.of("{ok: 1, n: 0, nModified: 0}", null),
        Arguments.of("{ok: 1, n: 1, nModified: 0}", null),
        Arguments.of("{ok: 1, n: 0, value: null}", null),
        Arguments.of("{ok: 1, n: 1, writeErrors: []}", null),
        Arguments.of("{ok: 1, writeErrors: [{code: 'private value'}]}", null),
        Arguments.of("{ok: 1, writeErrors: [{code: -1}]}", null),
        Arguments.of("{ok: 1, writeConcernError: {code: 9223372036854775807}}", null));
  }
}
