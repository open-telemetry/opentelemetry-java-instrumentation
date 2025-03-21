/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otel4s.v0_12;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.util.logging.Logger;

/**
 * A {@link BeforeAgentListener} that installs {@link Otel4sFiberContextBridge} if
 * `IOLocalStorageContext` is present in the classpath.
 */
@AutoService(BeforeAgentListener.class)
public class Otel4sFiberContextBridgeInstaller implements BeforeAgentListener {

  private static final Logger logger =
      Logger.getLogger(Otel4sFiberContextBridgeInstaller.class.getName());

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ContextStorage.addWrapper(Otel4sFiberContextBridge::new);
    logger.info("Installed Otel4sFiberContextBridge");
  }
}
