/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import javax.annotation.Nullable;
import org.elasticsearch.client.support.AbstractClient;

public class ElasticsearchTransportServerTargets {
  private static final VirtualField<AbstractClient, DbServerTarget> SERVER_TARGET =
      VirtualField.find(AbstractClient.class, DbServerTarget.class);
  private static final VirtualField<AbstractClient, AbstractClient> DELEGATE =
      VirtualField.find(AbstractClient.class, AbstractClient.class);
  private static final VirtualField<AbstractClient, UpdateLock> UPDATE_LOCK =
      VirtualField.find(AbstractClient.class, UpdateLock.class);

  public static void initializeUpdateLock(AbstractClient client) {
    if (UPDATE_LOCK.get(client) == null) {
      UPDATE_LOCK.set(client, new UpdateLock());
    }
  }

  @Nullable
  public static Object getUpdateLock(AbstractClient client) {
    return UPDATE_LOCK.get(client);
  }

  public static void update(
      AbstractClient client,
      @Nullable List<ElasticsearchTransportServerTarget.Endpoint> endpoints) {
    SERVER_TARGET.set(client, ElasticsearchTransportServerTarget.of(endpoints));
  }

  public static void setDelegate(AbstractClient client, Object delegate) {
    if (delegate instanceof AbstractClient) {
      DELEGATE.set(client, (AbstractClient) delegate);
    }
  }

  @Nullable
  public static DbServerTarget get(AbstractClient client) {
    DbServerTarget target = SERVER_TARGET.get(client);
    if (target != null) {
      return target;
    }
    AbstractClient delegate = DELEGATE.get(client);
    return delegate == null ? null : get(delegate);
  }

  private ElasticsearchTransportServerTargets() {}

  private static class UpdateLock {}
}
