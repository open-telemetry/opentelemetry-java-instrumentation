/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import java.util.function.Supplier;

public class Context {
  public static final Supplier<Context> FACTORY = Context::new;

  public int count = 0;
}
