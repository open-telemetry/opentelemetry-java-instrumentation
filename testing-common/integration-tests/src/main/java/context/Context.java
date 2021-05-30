/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import io.opentelemetry.javaagent.instrumentation.api.ContextStore;

public class Context {
  public static final ContextStore.Factory<Context> FACTORY = Context::new;

  public int count = 0;
}
