/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.env.CoreEnvironment;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

class CouchbaseProtostellarTargetsTest {

  @Test
  void preservesConfiguredTargetForLegacyCore() throws ReflectiveOperationException {
    Class.forName("com.couchbase.client.core.CoreProtostellar");

    Core core = new ObjenesisStd().newInstance(Core.class);

    Class<?> targetClass =
        Class.forName(
            "io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget");
    Class<?> connectionStringsClass =
        Class.forName(
            "io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseConnectionStrings");
    Object configuredTarget =
        connectionStringsClass
            .getMethod("target", String.class)
            .invoke(null, "protostellar://node:18099");

    Class<?> serverTargetsClass =
        Class.forName(
            "io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTargets");
    serverTargetsClass
        .getMethod("register", Core.class, targetClass, CoreEnvironment.class)
        .invoke(null, core, configuredTarget, null);

    Class<?> protostellarTargetsClass =
        Class.forName(
            "io.opentelemetry.javaagent.instrumentation.couchbase.v3_2.CouchbaseProtostellarTargets");
    protostellarTargetsClass
        .getMethod("registerCore", Core.class, Set.class)
        .invoke(null, core, singleton(SeedNode.create("node").withProtostellarPort(18099)));

    Object target = serverTargetsClass.getMethod("get", Core.class).invoke(null, core);
    assertThat(targetClass.getMethod("getAddress").invoke(target)).isEqualTo("node");
    assertThat(targetClass.getMethod("getPort").invoke(target)).isEqualTo(18099);
  }
}
