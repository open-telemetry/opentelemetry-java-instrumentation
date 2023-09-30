/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import javax.annotation.Nullable;

class MongoNetworkAttributesGetter implements ServerAttributesGetter<CommandStartedEvent, Void> {

  @Nullable
  @Override
  public String getServerAddress(CommandStartedEvent event) {
    if (event.getConnectionDescription() != null
        && event.getConnectionDescription().getServerAddress() != null) {
      return event.getConnectionDescription().getServerAddress().getHost();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(CommandStartedEvent event) {
    if (event.getConnectionDescription() != null
        && event.getConnectionDescription().getServerAddress() != null) {
      return event.getConnectionDescription().getServerAddress().getPort();
    }
    return null;
  }
}
