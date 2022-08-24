/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

class MongoNetAttributesGetter implements NetClientAttributesGetter<CommandStartedEvent, Void> {

  @Override
  @Nullable
  public String transport(CommandStartedEvent event, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(CommandStartedEvent event, @Nullable Void unused) {
    if (event.getConnectionDescription() != null
        && event.getConnectionDescription().getServerAddress() != null) {
      return event.getConnectionDescription().getServerAddress().getHost();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(CommandStartedEvent event, @Nullable Void unused) {
    if (event.getConnectionDescription() != null
        && event.getConnectionDescription().getServerAddress() != null) {
      return event.getConnectionDescription().getServerAddress().getPort();
    }
    return null;
  }

  @Nullable
  @Override
  public String sockFamily(CommandStartedEvent event, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerAddr(CommandStartedEvent event, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerName(CommandStartedEvent event, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public Integer sockPeerPort(CommandStartedEvent event, @Nullable Void unused) {
    return null;
  }
}
