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
  private static final VirtualField<AbstractClient, UpdateState> UPDATE_STATE =
      VirtualField.find(AbstractClient.class, UpdateState.class);

  public static void initializeUpdateState(AbstractClient client) {
    if (UPDATE_STATE.get(client) == null) {
      UPDATE_STATE.set(client, new UpdateState());
    }
  }

  @Nullable
  public static UpdateToken beginUpdate(AbstractClient client) {
    UpdateState state = UPDATE_STATE.get(client);
    if (state == null) {
      return null;
    }
    synchronized (state.lock) {
      return new UpdateToken(state, ++state.generation);
    }
  }

  public static void update(
      AbstractClient client,
      @Nullable List<ElasticsearchTransportServerTarget.Endpoint> endpoints) {
    UpdateToken token = beginUpdate(client);
    if (token != null) {
      update(client, token, endpoints);
    }
  }

  public static void update(
      AbstractClient client,
      @Nullable UpdateToken token,
      @Nullable List<ElasticsearchTransportServerTarget.Endpoint> endpoints) {
    DbServerTarget target = ElasticsearchTransportServerTarget.of(endpoints);
    UpdateState state = UPDATE_STATE.get(client);
    if (token == null || state != token.state) {
      return;
    }
    synchronized (state.lock) {
      if (token.generation == state.generation) {
        SERVER_TARGET.set(client, target);
      }
    }
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

  public static final class UpdateToken {
    private final UpdateState state;
    private final long generation;

    private UpdateToken(UpdateState state, long generation) {
      this.state = state;
      this.generation = generation;
    }
  }

  private static class UpdateState {
    private final Object lock = new Object();
    private long generation;
  }
}
