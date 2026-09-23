/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.jms.MessageListener;

public final class CamelJmsProcessingOwnership {

  private static final VirtualField<MessageListener, Boolean> CAMEL_OWNS_PROCESSING =
      VirtualField.find(MessageListener.class, Boolean.class);

  public static void markCamelAsProcessingOwner(MessageListener messageListener) {
    CAMEL_OWNS_PROCESSING.set(messageListener, true);
  }

  private CamelJmsProcessingOwnership() {}
}
