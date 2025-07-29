/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.stableDbSystemName;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;

import com.mongodb.client.MongoClient;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

abstract class AbstractMongodbSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired protected MongoClient mongoClient;

  @SuppressWarnings("deprecation") // uses deprecated semconv
  @Test
  void mongodb() {
    testing.runWithSpan(
        "server",
        () -> {
          mongoClient.listDatabaseNames().into(new ArrayList<>());
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("server"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasName("listDatabases admin")
                        .hasAttribute(
                            maybeStable(DbIncubatingAttributes.DB_SYSTEM),
                            stableDbSystemName(
                                DbIncubatingAttributes.DbSystemIncubatingValues.MONGODB))));
  }
}
