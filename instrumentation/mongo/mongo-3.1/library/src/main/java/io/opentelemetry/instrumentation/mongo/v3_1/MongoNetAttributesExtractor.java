/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesExtractor;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

class MongoNetAttributesExtractor
    extends InetSocketAddressNetClientAttributesExtractor<CommandStartedEvent, Void> {
  @Override
  public @Nullable InetSocketAddress getAddress(CommandStartedEvent event, @Nullable Void unused) {
    if (event.getConnectionDescription() != null
        && event.getConnectionDescription().getServerAddress() != null) {
      return event.getConnectionDescription().getServerAddress().getSocketAddress();
    } else {
      return null;
    }
  }

  @Override
  public @Nullable String transport(
      CommandStartedEvent commandStartedEvent, @Nullable Void unused) {
    return null;
  }
}
