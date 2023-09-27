/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.injection;

public interface ProxyInjectionBuilder {

  void inject(InjectionMode mode);
}
