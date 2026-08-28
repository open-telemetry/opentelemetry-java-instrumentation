/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import com.couchbase.client.core.ClusterFacade;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import javax.annotation.Nullable;

// Every object handed out by a cluster dispatches through the same core, which makes that core the
// carrier of the target. A missing target lets legacy telemetry use the contacted node.
public class CouchbaseCoreTargets {

  private static final VirtualField<ClusterFacade, CouchbaseServerTarget> SERVER_TARGET =
      VirtualField.find(ClusterFacade.class, CouchbaseServerTarget.class);

  public static void register(ClusterFacade core, @Nullable CouchbaseServerTarget target) {
    if (target != null) {
      SERVER_TARGET.set(core, target);
    }
  }

  @Nullable
  public static CouchbaseServerTarget get(@Nullable ClusterFacade core) {
    return core == null ? null : SERVER_TARGET.get(core);
  }

  private CouchbaseCoreTargets() {}
}
