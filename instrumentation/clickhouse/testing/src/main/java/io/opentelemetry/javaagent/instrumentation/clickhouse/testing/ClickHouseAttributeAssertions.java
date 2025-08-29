/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.testing;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.List;

@SuppressWarnings("deprecation") // using deprecated semconv
public final class ClickHouseAttributeAssertions {

  public static List<AttributeAssertion> attributeAssertions(
      String dbName, String host, int port, String statement, String operation) {
    return asList(
        equalTo(maybeStable(DB_SYSTEM), DbIncubatingAttributes.DbSystemIncubatingValues.CLICKHOUSE),
        equalTo(maybeStable(DB_NAME), dbName),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(maybeStable(DB_STATEMENT), statement),
        equalTo(maybeStable(DB_OPERATION), operation));
  }

  private ClickHouseAttributeAssertions() {}
}
