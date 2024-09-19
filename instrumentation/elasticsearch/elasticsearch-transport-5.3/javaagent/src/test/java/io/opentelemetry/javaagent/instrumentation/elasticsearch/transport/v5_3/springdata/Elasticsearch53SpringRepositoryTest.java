/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.springdata;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
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
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SearchAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.action"), "SearchAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request"), "SearchRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.search.types"),
                                "doc"))));
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
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "IndexAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.action"), "IndexAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request"), "IndexRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.write.type"), "doc"),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.request.write.version"), -3),
                            equalTo(AttributeKey.longKey("elasticsearch.response.status"), 201),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.failed"), 0),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.successful"),
                                1),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.total"), 2)),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "RefreshAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.action"), "RefreshAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request"), "RefreshRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.failed"), 0),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.successful"),
                                5),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.total"), 10))));
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
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "GetAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.action"), "GetAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.request"), "GetRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(AttributeKey.stringKey("elasticsearch.type"), "doc"),
                            equalTo(AttributeKey.stringKey("elasticsearch.id"), "1"),
                            equalTo(AttributeKey.longKey("elasticsearch.version"), 1))));
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
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "IndexAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.action"), "IndexAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request"), "IndexRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.write.type"), "doc"),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.request.write.version"), -3),
                            equalTo(AttributeKey.longKey("elasticsearch.response.status"), 200),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.failed"), 0),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.successful"),
                                1),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.total"), 2)),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "RefreshAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.action"), "RefreshAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request"), "RefreshRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.failed"), 0),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.successful"),
                                5),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.total"), 10))),
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
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "GetAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.action"), "GetAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.request"), "GetRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(AttributeKey.stringKey("elasticsearch.type"), "doc"),
                            equalTo(AttributeKey.stringKey("elasticsearch.id"), "1"),
                            equalTo(AttributeKey.longKey("elasticsearch.version"), 2))));
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
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "DeleteAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.action"), "DeleteAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request"), "DeleteRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.write.type"), "doc"),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.request.write.version"), -3),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.failed"), 0),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.successful"),
                                1),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.replication.total"), 2)),
                span ->
                    span.hasName("RefreshAction")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "RefreshAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.action"), "RefreshAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request"), "RefreshRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.failed"), 0),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.successful"),
                                5),
                            equalTo(
                                AttributeKey.longKey("elasticsearch.shard.broadcast.total"), 10))),
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
                                DbIncubatingAttributes.DB_SYSTEM,
                                DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SearchAction"),
                            equalTo(AttributeKey.stringKey("elasticsearch.action"), "SearchAction"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request"), "SearchRequest"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.indices"),
                                "test-index"),
                            equalTo(
                                AttributeKey.stringKey("elasticsearch.request.search.types"),
                                "doc"))));
  }
}
