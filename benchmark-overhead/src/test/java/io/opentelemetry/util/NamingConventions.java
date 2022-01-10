/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.util;

/** An container to hold both the local and container naming conventions. */
public class NamingConventions {

  public final NamingConvention container = new NamingConvention("/results");
  public final NamingConvention local = new NamingConvention(".");

  /** @return Root path for the local naming convention (where results are output) */
  public String localResults() {
    return local.root();
  }

  /** @return Root path for the container naming convention (where results are output) */
  public String containerResults() {
    return container.root();
  }
}
