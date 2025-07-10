/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.code.SemconvCodeStabilityUtil.codeFunctionAssertions;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static java.util.Arrays.asList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import spock.util.environment.Jvm;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Elasticsearch53SpringRepositoryTest extends ElasticsearchSpringTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension autoCleanup = AutoCleanupExtension.create();

  @BeforeAll
  void setUp(@TempDir File esWorkingDir) {
    Config.esWorkingDir = esWorkingDir;
  }

  private static DocRepository repository() {
    // when running on jdk 21 this test occasionally fails with timeout
    Assumptions.assumeTrue(
        Boolean.getBoolean("testLatestDeps")
            || !Jvm.getCurrent().isJava21Compatible()
            || Boolean.getBoolean("collectMetadata"));

    DocRepository result =
        testing.runWithSpan(
            "setup",
            () -> {
              AnnotationConfigApplicationContext context =
                  new AnnotationConfigApplicationContext(Config.class);
              autoCleanup.deferCleanup(context);
              DocRepository repo = context.getBean(DocRepository.class);
              repo.deleteAll();
              return repo;
            });

    testing.waitForTraces(1);
    testing.clearData();

    return result;
  }

  @Test
  void emptyRepository() {
    Iterable<Doc> result = repository().findAll();

    assertThat(result.iterator().hasNext()).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.findAll")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertFunctionName("findAll")),
                span ->
                    span.hasName("SearchAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "SearchAction"),
                            equalTo(
                                stringKey("elasticsearch.action"), experimental("SearchAction")),
                            equalTo(
                                stringKey("elasticsearch.request"), experimental("SearchRequest")),
                            equalTo(
                                stringKey("elasticsearch.request.indices"),
                                experimental("test-index")),
                            equalTo(
                                stringKey("elasticsearch.request.search.types"),
                                experimental("doc")))));
  }

  @Test
  void crud() {
    DocRepository repository = repository();
    Doc doc = new Doc();

    assertThat(repository.index(doc)).isEqualTo(doc);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.index")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertFunctionName("index")),
                span ->
                    span.hasName("IndexAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(indexActionAssertions(201)),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(refreshActionAssertions())));
    testing.clearData();

    assertThat(repository.findById("1").get()).isEqualTo(doc);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.findById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertFunctionName("findById")),
                span ->
                    span.hasName("GetAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(getActionAssertions(1))));
    testing.clearData();

    doc.setData("other data");

    assertThat(repository.index(doc)).isEqualTo(doc);
    assertThat(repository.findById("1").get()).isEqualTo(doc);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.index")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertFunctionName("index")),
                span ->
                    span.hasName("IndexAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(indexActionAssertions(200)),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(refreshActionAssertions())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.findById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertFunctionName("findById")),
                span ->
                    span.hasName("GetAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(getActionAssertions(2))));
    testing.clearData();

    repository.deleteById("1");
    assertThat(repository.findAll().iterator().hasNext()).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.deleteById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertFunctionName("deleteById")),
                span ->
                    span.hasName("DeleteAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "DeleteAction"),
                            equalTo(
                                stringKey("elasticsearch.action"), experimental("DeleteAction")),
                            equalTo(
                                stringKey("elasticsearch.request"), experimental("DeleteRequest")),
                            equalTo(
                                stringKey("elasticsearch.request.indices"),
                                experimental("test-index")),
                            equalTo(
                                stringKey("elasticsearch.request.write.type"), experimental("doc")),
                            equalTo(
                                longKey("elasticsearch.request.write.version"), experimental(-3)),
                            equalTo(
                                longKey("elasticsearch.shard.replication.failed"), experimental(0)),
                            equalTo(
                                longKey("elasticsearch.shard.replication.successful"),
                                experimental(1)),
                            equalTo(
                                longKey("elasticsearch.shard.replication.total"), experimental(2))),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(refreshActionAssertions())),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.findAll")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(assertFunctionName("findAll")),
                span ->
                    span.hasName("SearchAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "SearchAction"),
                            equalTo(
                                stringKey("elasticsearch.action"), experimental("SearchAction")),
                            equalTo(
                                stringKey("elasticsearch.request"), experimental("SearchRequest")),
                            equalTo(
                                stringKey("elasticsearch.request.indices"),
                                experimental("test-index")),
                            equalTo(
                                stringKey("elasticsearch.request.search.types"),
                                experimental("doc")))));
  }

  private static List<AttributeAssertion> indexActionAssertions(long status) {
    return new ArrayList<>(
        asList(
            equalTo(
                maybeStable(DB_SYSTEM),
                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
            equalTo(maybeStable(DB_OPERATION), "IndexAction"),
            equalTo(stringKey("elasticsearch.action"), experimental("IndexAction")),
            equalTo(stringKey("elasticsearch.request"), experimental("IndexRequest")),
            equalTo(stringKey("elasticsearch.request.indices"), experimental("test-index")),
            equalTo(stringKey("elasticsearch.request.write.type"), experimental("doc")),
            equalTo(longKey("elasticsearch.request.write.version"), experimental(-3)),
            equalTo(longKey("elasticsearch.response.status"), experimental(status)),
            equalTo(longKey("elasticsearch.shard.replication.failed"), experimental(0)),
            equalTo(longKey("elasticsearch.shard.replication.successful"), experimental(1)),
            equalTo(longKey("elasticsearch.shard.replication.total"), experimental(2))));
  }

  private static List<AttributeAssertion> refreshActionAssertions() {
    return new ArrayList<>(
        asList(
            equalTo(
                maybeStable(DB_SYSTEM),
                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
            equalTo(maybeStable(DB_OPERATION), "RefreshAction"),
            equalTo(stringKey("elasticsearch.action"), experimental("RefreshAction")),
            equalTo(stringKey("elasticsearch.request"), experimental("RefreshRequest")),
            equalTo(stringKey("elasticsearch.request.indices"), experimental("test-index")),
            equalTo(longKey("elasticsearch.shard.broadcast.failed"), experimental(0)),
            equalTo(longKey("elasticsearch.shard.broadcast.successful"), experimental(5)),
            equalTo(longKey("elasticsearch.shard.broadcast.total"), experimental(10))));
  }

  private static List<AttributeAssertion> getActionAssertions(long version) {
    return new ArrayList<>(
        asList(
            equalTo(
                maybeStable(DB_SYSTEM),
                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
            equalTo(maybeStable(DB_OPERATION), "GetAction"),
            equalTo(stringKey("elasticsearch.action"), experimental("GetAction")),
            equalTo(stringKey("elasticsearch.request"), experimental("GetRequest")),
            equalTo(stringKey("elasticsearch.request.indices"), experimental("test-index")),
            equalTo(stringKey("elasticsearch.type"), experimental("doc")),
            equalTo(stringKey("elasticsearch.id"), experimental("1")),
            equalTo(longKey("elasticsearch.version"), experimental(version))));
  }

  private static List<AttributeAssertion> assertFunctionName(String methodName) {
    return codeFunctionAssertions(DocRepository.class, methodName);
  }
}
