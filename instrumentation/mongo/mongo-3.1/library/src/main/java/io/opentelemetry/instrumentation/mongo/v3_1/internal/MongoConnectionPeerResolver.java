/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.connection.ConnectionDescription;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@FunctionalInterface
public interface MongoConnectionPeerResolver {

  @Nullable
  MongoNetworkPeer resolve(ConnectionDescription connectionDescription);
}
