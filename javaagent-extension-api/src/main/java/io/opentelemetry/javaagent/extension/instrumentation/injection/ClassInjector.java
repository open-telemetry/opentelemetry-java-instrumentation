/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.injection;

public interface ClassInjector {

  ProxyInjectionBuilder proxyBuilder(String classToProxy, String newProxyName);
}
