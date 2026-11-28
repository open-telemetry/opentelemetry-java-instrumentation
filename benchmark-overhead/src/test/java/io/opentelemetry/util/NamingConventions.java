/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.util;

/** A container to hold both the local and container naming conventions. */
public class NamingConventions {

  public final NamingConvention container;
  public final NamingConvention local;

  public NamingConventions() {
    this(".", "petclinic");
  }

  public NamingConventions(String localDir, String workload) {
    local = new LocalNamingConvention(localDir, workload);
    container = new ContainerNamingConvention("/results", workload);
  }

  /**
   * @return Root path for the local naming convention (where results are output)
   */
  public String localResults() {
    return local.root();
  }

  /**
   * @return Root path for the container naming convention (where results are output)
   */
  public String containerResults() {
    return container.root();
  }
}
