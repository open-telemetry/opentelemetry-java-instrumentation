/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import java.util.regex.Pattern;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time. Exposed for {@link io.nats.client.impl.OpenTelemetryDispatcherFactory}.
 */
public class NatsSubjectPattern {

  private NatsSubjectPattern() {}

  public static Pattern compile(String subject) {
    return Pattern.compile(
        "^" + subject.replace(".", "\\.").replace(">", "*").replace("*", ".*") + "$");
  }
}
