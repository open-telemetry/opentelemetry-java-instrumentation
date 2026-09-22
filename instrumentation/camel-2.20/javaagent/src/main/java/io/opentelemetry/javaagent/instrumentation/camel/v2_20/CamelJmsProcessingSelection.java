/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.jms.MessageListener;

public final class CamelJmsProcessingSelection {

  private static final VirtualField<MessageListener, Boolean> PROCESSING_SELECTION =
      VirtualField.find(MessageListener.class, Boolean.class);

  public static void selectFrameworkProcessing(MessageListener messageListener) {
    PROCESSING_SELECTION.set(messageListener, true);
  }

  private CamelJmsProcessingSelection() {}
}
