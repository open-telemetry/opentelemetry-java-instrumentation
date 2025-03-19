/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.couchbase.springdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.view.DefaultView;
import com.couchbase.client.java.view.DesignDocument;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.couchbase.AbstractCouchbaseTest;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Collections;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public abstract class AbstractCouchbaseSpringRepositoryTest extends AbstractCouchbaseTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private ConfigurableApplicationContext applicationContext;
  private TestRepository repository;

  @BeforeAll
  void setUp() {
    CouchbaseEnvironment environment = envBuilder(bucketCouchbase).build();
    Cluster couchbaseCluster =
        CouchbaseCluster.create(environment, Collections.singletonList("127.0.0.1"));

    // Create view for SpringRepository's findAll()
    couchbaseCluster
        .openBucket(bucketCouchbase.name(), bucketCouchbase.password())
        .bucketManager()
        .insertDesignDocument(
            DesignDocument.create(
                "testDocument",
                Collections.singletonList(
                    DefaultView.create(
                        "all",
                        "function (doc, meta) {"
                            + "  if (doc._class == \"io.opentelemetry.instrumentation.couchbase.springdata.TestDocument\") {"
                            + "    emit(meta.id, null);"
                            + " }"
                            + "}"))));
    CouchbaseConfig.environment = environment;
    CouchbaseConfig.bucketSettings = bucketCouchbase;

    // Close all buckets and disconnect
    couchbaseCluster.disconnect();

    applicationContext = new AnnotationConfigApplicationContext(CouchbaseConfig.class);
    repository = applicationContext.getBean(TestRepository.class);
  }

  private void cleanUpTest() {
    testing.clearData();
    repository.deleteAll();
    testing.waitForTraces(2);
  }

  @AfterAll
  void cleanUp() {
    applicationContext.close();
  }

  protected abstract TestDocument findById(TestRepository repository, String id);

  protected abstract void deleteById(TestRepository repository, String id);

  @Test
  void emptyRepo() {
    Iterable<TestDocument> result = repository.findAll();

    assertThat(result.iterator().hasNext()).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertCouchbaseSpan(
                            span,
                            bucketCouchbase.name(),
                            null,
                            bucketCouchbase.name(),
                            "ViewQuery(testDocument/all)")
                        .hasNoParent()));
  }

  @Test
  void save() {
    TestDocument document = new TestDocument();
    TestDocument result = repository.save(document);
    cleanup.deferCleanup(this::cleanUpTest);

    assertThat(result).isEqualTo(document);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    assertCouchbaseSpan(span, "Bucket.upsert", bucketCouchbase.name())
                        .hasNoParent()));
  }

  @Test
  void saveAndRetrieve() {
    TestDocument document = new TestDocument();
    TestDocument result =
        testing.runWithSpan(
            "someTrace",
            () -> {
              repository.save(document);
              return findById(repository, "1");
            });
    cleanup.deferCleanup(this::cleanUpTest);

    assertThat(result).isEqualTo(document);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCouchbaseSpan(span, "Bucket.upsert", bucketCouchbase.name())
                        .hasParent(trace.getSpan(0)),
                span ->
                    assertCouchbaseSpan(span, "Bucket.get", bucketCouchbase.name())
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void saveAndUpdate() {
    TestDocument document = new TestDocument();
    testing.runWithSpan(
        "someTrace",
        () -> {
          repository.save(document);
          document.setData("other data");
          repository.save(document);
        });
    cleanup.deferCleanup(this::cleanUpTest);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCouchbaseSpan(span, "Bucket.upsert", bucketCouchbase.name())
                        .hasParent(trace.getSpan(0)),
                span ->
                    assertCouchbaseSpan(span, "Bucket.upsert", bucketCouchbase.name())
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void saveAndDelete() {
    TestDocument document = new TestDocument();
    boolean found =
        testing.runWithSpan(
            "someTrace",
            () -> {
              repository.save(document);
              deleteById(repository, "1");
              return repository.findAll().iterator().hasNext();
            });

    assertThat(found).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertCouchbaseSpan(span, "Bucket.upsert", bucketCouchbase.name())
                        .hasParent(trace.getSpan(0)),
                span ->
                    assertCouchbaseSpan(span, "Bucket.remove", bucketCouchbase.name())
                        .hasParent(trace.getSpan(0)),
                span ->
                    assertCouchbaseSpan(
                            span,
                            bucketCouchbase.name(),
                            null,
                            bucketCouchbase.name(),
                            "ViewQuery(testDocument/all)")
                        .hasParent(trace.getSpan(0))));
  }
}
