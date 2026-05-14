/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.log4j.spi.LoggingEvent;

public class VirtualFieldHelper {

  public static final VirtualField<LoggingEvent, Context> CONTEXT =
      VirtualField.find(LoggingEvent.class, Context.class);

  private VirtualFieldHelper() {}
}
