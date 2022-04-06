/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bootstrap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.muzzle.BootstrapProxyProvider;

@AutoService(BootstrapProxyProvider.class)
public class BootstrapProxyProviderImpl implements BootstrapProxyProvider {

  @Override
  public ClassLoader getBootstrapProxy() {
    return Utils.getBootstrapProxy();
  }
}
