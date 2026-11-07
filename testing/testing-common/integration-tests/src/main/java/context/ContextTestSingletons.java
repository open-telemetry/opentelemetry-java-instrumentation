/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import library.KeyClass;
import library.KeyInterface;

public class ContextTestSingletons {

  public static final VirtualField<KeyClass, Context> CONTEXT =
      VirtualField.find(KeyClass.class, Context.class);
  public static final VirtualField<KeyClass, Integer> INTEGER =
      VirtualField.find(KeyClass.class, Integer.class);
  public static final VirtualField<KeyInterface, Integer> INTERFACE_INTEGER =
      VirtualField.find(KeyInterface.class, Integer.class);

  private ContextTestSingletons() {}
}
