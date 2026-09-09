/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParseContextTest {

  @Test
  void parserDefaultsDoNotCreateConfiguredTarget() {
    ParseContext context = ParseContext.of("postgresql", null);
    context.defaultHost("localhost");
    context.defaultPort(5432);

    DbInfo info = context.toDbInfo();

    assertThat(info.getLegacyServerAddress()).isEqualTo("localhost");
    assertThat(info.getLegacyServerPort()).isEqualTo(5432);
    assertThat(info.getConfiguredServerTarget()).isNull();
  }

  @Test
  void configuredHostKeepsParserDefaultPort() {
    ParseContext context = ParseContext.of("postgresql", null);
    context.defaultPort(5432);
    context.host("[2001:db8::1]");

    assertThat(context.toDbInfo().getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("2001:db8::1", 5432));
  }

  @ParameterizedTest
  @ValueSource(ints = {5432, 5433})
  void configuredPortIsPreservedWhenSetBeforeHost(int port) {
    ParseContext context = ParseContext.of("postgresql", null);
    context.defaultPort(5432);
    context.port(port);
    context.host("pg.host");

    assertThat(context.toDbInfo().getConfiguredServerTarget())
        .isEqualTo(DbServerTarget.create("pg.host", port));
  }

  @Test
  void singleServerFallbackPreservesConfiguredValuesWhenDefaultsChange() {
    ParseContext context = ParseContext.of("postgresql", null);
    context.host("pg.host");
    context.port(5433);
    context.defaultHost("localhost");
    context.defaultPort(5432);

    DbInfo info = context.toDbInfo();

    assertThat(info.getLegacyServerAddress()).isEqualTo("localhost");
    assertThat(info.getLegacyServerPort()).isEqualTo(5432);
    assertThat(info.getConfiguredServerTarget()).isEqualTo(DbServerTarget.create("pg.host", 5433));
  }

  @Test
  void resolvedGroupIsNotOverwrittenByLegacyEndpoint() {
    ParseContext context = ParseContext.of("postgresql", null);
    DbServerTarget target = DbServerTarget.create("h1:5432,h2:5433", null);
    context.disableSingleServerFallback();
    context.resolveConfiguredServerTarget(target);
    context.host("h1");
    context.port(5432);

    assertThat(context.toDbInfo().getConfiguredServerTarget()).isSameAs(target);
  }

  @Test
  void disablingFallbackPreservesResolvedTarget() {
    ParseContext context = ParseContext.of("postgresql", null);
    DbServerTarget target = DbServerTarget.create("h1:5432,h2:5433", null);
    context.resolveConfiguredServerTarget(target);
    context.disableSingleServerFallback();

    assertThat(context.toDbInfo().getConfiguredServerTarget()).isSameAs(target);
  }

  @Test
  void rejectedTargetDoesNotFallBackToLegacyEndpoint() {
    ParseContext context = ParseContext.of("postgresql", null);
    context.resolveConfiguredServerTarget(null);
    context.host("h1");
    context.port(5432);

    assertThat(context.toDbInfo().getConfiguredServerTarget()).isNull();
  }

  @Test
  void resolvingNullClearsTargetWithoutRestoringFallback() {
    ParseContext context = ParseContext.of("postgresql", null);
    context.host("h1");
    context.port(5432);
    context.resolveConfiguredServerTarget(DbServerTarget.create("h1:5432,h2:5433", null));
    context.resolveConfiguredServerTarget(null);

    assertThat(context.toDbInfo().getConfiguredServerTarget()).isNull();
  }

  @Test
  void unresolvedGroupDoesNotFallBackToSingleEndpoint() {
    ParseContext context = ParseContext.of("postgresql", null);
    context.host("h1");
    context.port(5432);
    context.disableSingleServerFallback();

    assertThat(context.toDbInfo().getConfiguredServerTarget()).isNull();
  }
}
