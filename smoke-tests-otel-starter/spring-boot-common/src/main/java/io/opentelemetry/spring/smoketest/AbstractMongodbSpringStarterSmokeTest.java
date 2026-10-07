/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.MONGODB;

import com.mongodb.client.MongoClient;
import io.opentelemetry.api.trace.SpanKind;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

abstract class AbstractMongodbSpringStarterSmokeTest extends AbstractSpringStarterSmokeTest {

  @Autowired protected MongoClient mongoClient;

  @Test
  void mongodb() {
    runMongoClientTest(
        () -> {
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
                              .hasAttribute(DB_SYSTEM_NAME, MONGODB)));
        });
  }

  protected void runMongoClientTest(Runnable runnable) {
    runnable.run();
  }
}
