/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.catseffect.v3_6;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.util.logging.Logger;

/**
 * A {@link BeforeAgentListener} that installs {@link FiberContextBridge} if `cats.effect.IO` is
 * present in the classpath.
 */
@AutoService(BeforeAgentListener.class)
public class FiberContextBridgeInstaller implements BeforeAgentListener {

  private static final Logger logger =
      Logger.getLogger(FiberContextBridgeInstaller.class.getName());

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ContextStorage.addWrapper(FiberContextBridge::new);
    logger.fine("Installed Cats Effect FiberContextBridge");
  }
}
