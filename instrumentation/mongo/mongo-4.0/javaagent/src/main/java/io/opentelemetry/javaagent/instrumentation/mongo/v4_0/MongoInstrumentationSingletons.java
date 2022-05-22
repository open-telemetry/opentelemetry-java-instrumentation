/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import com.mongodb.event.CommandListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;

public final class MongoInstrumentationSingletons {

  public static final CommandListener LISTENER =
      MongoTelemetry.create(GlobalOpenTelemetry.get()).newCommandListener();

  private MongoInstrumentationSingletons() {}
}
