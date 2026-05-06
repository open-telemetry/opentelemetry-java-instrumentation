/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;

public class PekkoRemoteSingletons {

  private static byte validateIdentifier(int instrumentIdentifier) {
    if (instrumentIdentifier < 8 || instrumentIdentifier > 31) {
      throw new IllegalArgumentException(
          "Instrument identifier must be value between 8 and 31 but is " + instrumentIdentifier);
    } else {
      return (byte) instrumentIdentifier;
    }
  }

  public static final byte INSTRUMENT_IDENTIFIER =
      validateIdentifier(
          DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "pekko_remote")
              .getInt("instrument_identifier", 8));

  private PekkoRemoteSingletons() {}
}
