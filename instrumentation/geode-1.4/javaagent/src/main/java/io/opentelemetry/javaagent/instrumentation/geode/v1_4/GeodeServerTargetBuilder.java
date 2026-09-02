/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import javax.annotation.Nullable;

class GeodeServerTargetBuilder {

  private static final int DEFAULT_SERVER_PORT = 40404;
  private static final int DEFAULT_LOCATOR_PORT = 10334;
  private static final int MAX_SERVER_ENDPOINTS = 5;

  private DbServerTargetBuilder servers = newServerBuilder();
  private DbServerTargetBuilder locators = newLocatorBuilder();
  @Nullable private String serverGroup;
  private boolean serverConfigured;
  private boolean locatorConfigured;

  synchronized void addServer(@Nullable String host, int port) {
    serverConfigured = true;
    servers.addEndpoint(host, port);
  }

  synchronized void addLocator(@Nullable String host, int port) {
    locatorConfigured = true;
    locators.addEndpoint(host, port);
  }

  synchronized void setServerGroup(@Nullable String serverGroup) {
    this.serverGroup = serverGroup;
  }

  synchronized void reset() {
    servers = newServerBuilder();
    locators = newLocatorBuilder();
    serverGroup = null;
    serverConfigured = false;
    locatorConfigured = false;
  }

  @Nullable
  synchronized DbServerTarget build() {
    if (serverConfigured) {
      return servers.build();
    }
    if (locatorConfigured) {
      return locators.setSuffix(serverGroup).build();
    }
    return null;
  }

  private static DbServerTargetBuilder newServerBuilder() {
    return DbServerTarget.builder(DEFAULT_SERVER_PORT)
        .setSorted(true)
        .setMaxEndpoints(MAX_SERVER_ENDPOINTS);
  }

  private static DbServerTargetBuilder newLocatorBuilder() {
    return DbServerTarget.builder(DEFAULT_LOCATOR_PORT)
        .setSorted(true)
        .setMaxEndpoints(Integer.MAX_VALUE)
        .setPortAlwaysInline(true);
  }
}
