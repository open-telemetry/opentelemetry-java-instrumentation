/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;

class VertxVirtualThreadHttpServerTest extends Vertx41HttpServerTest {
  @Override
  protected void configureDeploymentOptions(DeploymentOptions options) {
    options.setThreadingModel(ThreadingModel.VIRTUAL_THREAD);
  }
}
