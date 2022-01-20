/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class MongoNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<CommandStartedEvent, Void> {
  @Override
  @Nullable
  public InetSocketAddress getAddress(CommandStartedEvent event, @Nullable Void unused) {
    if (event.getConnectionDescription() != null
        && event.getConnectionDescription().getServerAddress() != null) {
      return event.getConnectionDescription().getServerAddress().getSocketAddress();
    } else {
      return null;
    }
  }

  @Override
  @Nullable
  public String transport(CommandStartedEvent commandStartedEvent, @Nullable Void unused) {
    return null;
  }
}
