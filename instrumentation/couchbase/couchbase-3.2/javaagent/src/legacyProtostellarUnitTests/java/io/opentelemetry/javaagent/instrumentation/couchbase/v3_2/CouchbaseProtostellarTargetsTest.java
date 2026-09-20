/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.SeedNode;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseConnectionStrings;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTargets;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

class CouchbaseProtostellarTargetsTest {

  @Test
  void preservesConfiguredTargetForLegacyCore() {
    Core core = new ObjenesisStd().newInstance(Core.class);

    CouchbaseServerTarget configuredTarget =
        CouchbaseConnectionStrings.target("protostellar://node:18099");
    CouchbaseServerTargets.register(core, configuredTarget, null);

    CouchbaseProtostellarTargets.registerCore(
        core, singleton(SeedNode.create("node").withProtostellarPort(18099)));

    CouchbaseServerTarget target = CouchbaseServerTargets.get(core);
    assertThat(target.getAddress()).isEqualTo("node");
    assertThat(target.getPort()).isEqualTo(18099);
  }
}
