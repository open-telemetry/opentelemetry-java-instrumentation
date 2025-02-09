/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.io.File;
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
class Elasticsearch53SpringRepositoryTest {

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
        Boolean.getBoolean("testLatestDeps") || !Jvm.getCurrent().isJava21Compatible());

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
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                DocRepository.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "findAll")),
                span ->
                    span.hasName("SearchAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "SearchAction"),
                            equalTo(stringKey("elasticsearch.action"), "SearchAction"),
                            equalTo(stringKey("elasticsearch.request"), "SearchRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(stringKey("elasticsearch.request.search.types"), "doc"))));
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                DocRepository.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "index")),
                span ->
                    span.hasName("IndexAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "IndexAction"),
                            equalTo(stringKey("elasticsearch.action"), "IndexAction"),
                            equalTo(stringKey("elasticsearch.request"), "IndexRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(stringKey("elasticsearch.request.write.type"), "doc"),
                            equalTo(longKey("elasticsearch.request.write.version"), -3),
                            equalTo(longKey("elasticsearch.response.status"), 201),
                            equalTo(longKey("elasticsearch.shard.replication.failed"), 0),
                            equalTo(longKey("elasticsearch.shard.replication.successful"), 1),
                            equalTo(longKey("elasticsearch.shard.replication.total"), 2)),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "RefreshAction"),
                            equalTo(stringKey("elasticsearch.action"), "RefreshAction"),
                            equalTo(stringKey("elasticsearch.request"), "RefreshRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(longKey("elasticsearch.shard.broadcast.failed"), 0),
                            equalTo(longKey("elasticsearch.shard.broadcast.successful"), 5),
                            equalTo(longKey("elasticsearch.shard.broadcast.total"), 10))));
    testing.clearData();

    assertThat(repository.findById("1").get()).isEqualTo(doc);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.findById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                DocRepository.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "findById")),
                span ->
                    span.hasName("GetAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "GetAction"),
                            equalTo(stringKey("elasticsearch.action"), "GetAction"),
                            equalTo(stringKey("elasticsearch.request"), "GetRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(stringKey("elasticsearch.type"), "doc"),
                            equalTo(stringKey("elasticsearch.id"), "1"),
                            equalTo(longKey("elasticsearch.version"), 1))));
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                DocRepository.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "index")),
                span ->
                    span.hasName("IndexAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "IndexAction"),
                            equalTo(stringKey("elasticsearch.action"), "IndexAction"),
                            equalTo(stringKey("elasticsearch.request"), "IndexRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(stringKey("elasticsearch.request.write.type"), "doc"),
                            equalTo(longKey("elasticsearch.request.write.version"), -3),
                            equalTo(longKey("elasticsearch.response.status"), 200),
                            equalTo(longKey("elasticsearch.shard.replication.failed"), 0),
                            equalTo(longKey("elasticsearch.shard.replication.successful"), 1),
                            equalTo(longKey("elasticsearch.shard.replication.total"), 2)),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "RefreshAction"),
                            equalTo(stringKey("elasticsearch.action"), "RefreshAction"),
                            equalTo(stringKey("elasticsearch.request"), "RefreshRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(longKey("elasticsearch.shard.broadcast.failed"), 0),
                            equalTo(longKey("elasticsearch.shard.broadcast.successful"), 5),
                            equalTo(longKey("elasticsearch.shard.broadcast.total"), 10))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.findById")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                DocRepository.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "findById")),
                span ->
                    span.hasName("GetAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "GetAction"),
                            equalTo(stringKey("elasticsearch.action"), "GetAction"),
                            equalTo(stringKey("elasticsearch.request"), "GetRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(stringKey("elasticsearch.type"), "doc"),
                            equalTo(stringKey("elasticsearch.id"), "1"),
                            equalTo(longKey("elasticsearch.version"), 2))));
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                DocRepository.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "deleteById")),
                span ->
                    span.hasName("DeleteAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "DeleteAction"),
                            equalTo(stringKey("elasticsearch.action"), "DeleteAction"),
                            equalTo(stringKey("elasticsearch.request"), "DeleteRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(stringKey("elasticsearch.request.write.type"), "doc"),
                            equalTo(longKey("elasticsearch.request.write.version"), -3),
                            equalTo(longKey("elasticsearch.shard.replication.failed"), 0),
                            equalTo(longKey("elasticsearch.shard.replication.successful"), 1),
                            equalTo(longKey("elasticsearch.shard.replication.total"), 2)),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "RefreshAction"),
                            equalTo(stringKey("elasticsearch.action"), "RefreshAction"),
                            equalTo(stringKey("elasticsearch.request"), "RefreshRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(longKey("elasticsearch.shard.broadcast.failed"), 0),
                            equalTo(longKey("elasticsearch.shard.broadcast.successful"), 5),
                            equalTo(longKey("elasticsearch.shard.broadcast.total"), 10))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DocRepository.findAll")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                DocRepository.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "findAll")),
                span ->
                    span.hasName("SearchAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                maybeStable(DB_SYSTEM),
                                DbIncubatingAttributes.DbSystemIncubatingValues.ELASTICSEARCH),
                            equalTo(maybeStable(DB_OPERATION), "SearchAction"),
                            equalTo(stringKey("elasticsearch.action"), "SearchAction"),
                            equalTo(stringKey("elasticsearch.request"), "SearchRequest"),
                            equalTo(stringKey("elasticsearch.request.indices"), "test-index"),
                            equalTo(stringKey("elasticsearch.request.search.types"), "doc"))));
  }
}
