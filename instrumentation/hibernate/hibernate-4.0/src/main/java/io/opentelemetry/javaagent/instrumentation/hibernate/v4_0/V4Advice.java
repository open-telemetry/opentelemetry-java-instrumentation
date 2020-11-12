/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import org.hibernate.SharedSessionContract;

public abstract class V4Advice {

  /**
   * Some cases of instrumentation will match more broadly than others, so this unused method allows
   * all instrumentation to uniformly match versions of Hibernate starting at 4.0.
   */
  public static void muzzleCheck(SharedSessionContract contract) {
    contract.createCriteria("");
  }
}
