/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context.client;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import sun.rmi.transport.Connection;

public class VirtualFieldHelper {

  // we need to use a dedicated helper class because the target of the virtual field requires
  // opening the jpms module. Trying to reuse other singletons make the instrumentation fail
  public static final VirtualField<Connection, Boolean> KNOWN_CONNECTION =
      VirtualField.find(Connection.class, Boolean.class);

  private VirtualFieldHelper() {}
}
