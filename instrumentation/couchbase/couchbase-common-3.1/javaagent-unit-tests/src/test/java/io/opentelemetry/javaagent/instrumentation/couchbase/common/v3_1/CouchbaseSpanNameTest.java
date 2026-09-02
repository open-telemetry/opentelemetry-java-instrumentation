/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import org.junit.jupiter.api.Test;

class CouchbaseSpanNameTest {

  @Test
  void serverOnlyFallbackIncludesNonDefaultPort() {
    CouchbaseSpanName spanName = new CouchbaseSpanName("get");
    CouchbaseServerTarget.Builder serverTarget = CouchbaseServerTarget.builder("couchbase");
    serverTarget.addSeed("node.example", 11211);

    spanName.captureServerTarget(serverTarget.build());
    assertThat(spanName.spanName()).isEqualTo("get node.example:11211");
  }

  @Test
  void serviceDiscoveryIdentityIsUsedUnchanged() {
    CouchbaseSpanName spanName = new CouchbaseSpanName("ping");

    spanName.captureServerTarget(
        CouchbaseServerTarget.forServiceDiscovery("couchbases", "cluster.example"));

    assertThat(spanName.spanName()).isEqualTo("ping couchbases://cluster.example");
  }

  @Test
  void scopedQueryUsesNamespace() {
    CouchbaseSpanName spanName = new CouchbaseSpanName("query");
    spanName.captureAttribute("db.namespace", "bucket");
    spanName.captureServerTarget(null);

    assertThat(spanName.spanName()).isEqualTo("query bucket");
  }

  @Test
  void collectionTakesPriorityRegardlessOfCallbackOrder() {
    CouchbaseSpanName spanName = new CouchbaseSpanName("get");
    CouchbaseServerTarget.Builder serverTarget = CouchbaseServerTarget.builder("couchbase");
    serverTarget.addSeed("node.example", 11211);

    spanName.captureServerTarget(serverTarget.build());
    spanName.captureAttribute("db.namespace", "bucket");
    spanName.captureAttribute("db.collection.name", "collection");
    spanName.captureAttribute("db.operation.name", "upsert");

    assertThat(spanName.spanName()).isEqualTo("upsert collection");
  }

  @Test
  void querySummaryTakesPriority() {
    CouchbaseSpanName spanName = new CouchbaseSpanName("query");

    spanName.captureAttribute("db.query.summary", "SELECT collection");
    spanName.captureAttribute("db.namespace", "bucket");
    spanName.captureServerTarget(null);
    spanName.captureAttribute("db.collection.name", "collection");

    assertThat(spanName.spanName()).isEqualTo("SELECT collection");
  }

  @Test
  void operationIsUsedWhenTargetIsUnavailable() {
    CouchbaseSpanName spanName = new CouchbaseSpanName("ping");
    spanName.captureServerTarget(null);

    assertThat(spanName.isDatabaseRequest()).isTrue();
    assertThat(spanName.spanName()).isEqualTo("ping");
  }

  @Test
  void internalSpanWithoutRequestContextIsNotRenamed() {
    CouchbaseSpanName spanName = new CouchbaseSpanName("dispatch_to_server");
    spanName.captureAttribute("db.collection.name", "_default");

    assertThat(spanName.isDatabaseRequest()).isFalse();
  }
}
