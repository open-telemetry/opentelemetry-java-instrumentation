/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.CoreContext;
import com.couchbase.client.core.env.CoreEnvironment;
import com.couchbase.client.core.env.SecurityConfig;
import com.couchbase.client.core.env.SeedNode;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseConnectionStrings;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTargets;
import org.junit.jupiter.api.Test;

class CouchbaseProtostellarTargetsTest {

  @Test
  void preservesConfiguredTargetForLegacyCore() {
    Core core = mock(Core.class);
    CoreContext context = mock(CoreContext.class);
    CoreEnvironment environment = mock(CoreEnvironment.class);
    when(core.context()).thenReturn(context);
    when(context.environment()).thenReturn(environment);
    when(environment.securityConfig()).thenReturn(SecurityConfig.create());

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
