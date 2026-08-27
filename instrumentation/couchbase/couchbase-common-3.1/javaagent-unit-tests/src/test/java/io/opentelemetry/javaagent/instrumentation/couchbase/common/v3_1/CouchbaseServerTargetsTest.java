/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.core.env.SeedNode;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CouchbaseServerTargetsTest {

  @Test
  void directSeedKeepsClusterManagerPortWhenKvPortIsMissing() {
    SeedNode seedNode = SeedNode.create("node.example", Optional.empty(), Optional.of(8091));

    CouchbaseServerTarget target = CouchbaseServerTargets.target(singleton(seedNode));

    assertThat(target.getAddress()).isEqualTo("node.example");
    assertThat(target.getPort()).isEqualTo(8091);
  }
}
