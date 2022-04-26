/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Some useful constants.
 *
 * <p>Idea here is to keep this class safe to inject into client's class loader.
 */
public final class Constants {

  /** packages which will be loaded on the bootstrap classloader. */
  public static final List<String> BOOTSTRAP_PACKAGE_PREFIXES =
      Collections.unmodifiableList(
          Arrays.asList(
              "io.opentelemetry.javaagent.bootstrap",
              "io.opentelemetry.javaagent.shaded",
              "io.opentelemetry.javaagent.slf4j"));

  private Constants() {}
}
