/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseConnectionStrings;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseServerTarget;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import javax.annotation.Nullable;

public final class CouchbaseProtostellarTargets {

  @Nullable
  private static final VirtualField<Object, CouchbaseServerTarget> CORE_TARGETS =
      virtualField("com.couchbase.client.core.CoreProtostellar");

  @Nullable
  private static final VirtualField<Object, CouchbaseServerTarget> REQUEST_TARGETS =
      virtualField("com.couchbase.client.core.protostellar.ProtostellarBaseRequest");

  public static void registerCore(Object core, @Nullable ConnectionString connectionString) {
    CouchbaseServerTarget target = CouchbaseConnectionStrings.target(connectionString);
    if (CORE_TARGETS != null && target != null) {
      CORE_TARGETS.set(core, target);
    }
  }

  public static void registerRequest(Object request, Object core) {
    if (CORE_TARGETS == null || REQUEST_TARGETS == null) {
      return;
    }
    CouchbaseServerTarget target = CORE_TARGETS.get(core);
    if (target != null) {
      REQUEST_TARGETS.set(request, target);
    }
  }

  @Nullable
  public static CouchbaseServerTarget getRequestTarget(@Nullable Object request) {
    return REQUEST_TARGETS == null || request == null ? null : REQUEST_TARGETS.get(request);
  }

  @NoMuzzle
  @SuppressWarnings("unchecked") // virtual field key type is not known at compile time
  @Nullable
  private static VirtualField<Object, CouchbaseServerTarget> virtualField(String className) {
    try {
      Class<?> carrierClass = Class.forName(className);
      return (VirtualField<Object, CouchbaseServerTarget>)
          VirtualField.find(carrierClass, CouchbaseServerTarget.class);
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  private CouchbaseProtostellarTargets() {}
}
