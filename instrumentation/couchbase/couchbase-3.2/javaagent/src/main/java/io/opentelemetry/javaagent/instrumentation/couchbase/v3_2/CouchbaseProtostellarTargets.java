/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.CoreProtostellar;
import com.couchbase.client.core.cnc.RequestSpan;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseConnectionStrings;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTargets;
import java.util.Set;
import javax.annotation.Nullable;

public final class CouchbaseProtostellarTargets {

  private static final VirtualField<CoreProtostellar, CouchbaseServerTarget> CORE_TARGETS =
      VirtualField.find(CoreProtostellar.class, CouchbaseServerTarget.class);

  public static void registerCore(
      CoreProtostellar core, @Nullable ConnectionString connectionString) {
    CouchbaseServerTarget target = CouchbaseConnectionStrings.target(connectionString);
    if (target != null) {
      CORE_TARGETS.set(core, target);
    }
  }

  public static void registerCore(Core core, Set<SeedNode> seedNodes) {
    if (CouchbaseServerTargets.get(core) == null) {
      CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes, core.context().environment());
    }
  }

  public static void captureRequestSpan(Core core, RequestSpan span) {
    CouchbaseRequestTracer.captureServerTarget(span, CouchbaseServerTargets.get(core));
  }

  public static void captureRequestSpan(CoreProtostellar core, RequestSpan span) {
    CouchbaseRequestTracer.captureServerTarget(span, CORE_TARGETS.get(core));
  }

  private CouchbaseProtostellarTargets() {}
}
