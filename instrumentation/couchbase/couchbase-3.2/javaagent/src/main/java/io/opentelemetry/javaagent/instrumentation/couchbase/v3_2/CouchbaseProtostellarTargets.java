/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import com.couchbase.client.core.CoreProtostellar;
import com.couchbase.client.core.protostellar.ProtostellarBaseRequest;
import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseConfiguredTarget;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseConnectionStrings;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget;
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

  public static void registerRequest(ProtostellarBaseRequest request, CoreProtostellar core) {
    CouchbaseServerTarget target = CORE_TARGETS.get(core);
    CouchbaseConfiguredTarget.registerRequestTarget(request, target);
  }

  private CouchbaseProtostellarTargets() {}
}
