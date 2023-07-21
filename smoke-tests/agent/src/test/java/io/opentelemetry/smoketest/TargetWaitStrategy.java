/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import java.time.Duration;

public abstract class TargetWaitStrategy {
  public final Duration timeout;

  protected TargetWaitStrategy(Duration timeout) {
    this.timeout = timeout;
  }

  public static class Log extends TargetWaitStrategy {
    public final String regex;

    public Log(Duration timeout, String regex) {
      super(timeout);
      this.regex = regex;
    }
  }
}
