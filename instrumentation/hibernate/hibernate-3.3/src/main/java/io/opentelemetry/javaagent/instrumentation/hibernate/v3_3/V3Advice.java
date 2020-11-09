/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import org.hibernate.classic.Validatable;
import org.hibernate.transaction.JBossTransactionManagerLookup;

public abstract class V3Advice {

  /**
   * Some cases of instrumentation will match more broadly than others, so this unused method allows
   * all instrumentation to uniformly match versions of Hibernate between 3.3 and 4.
   */
  public static void muzzleCheck(
      // Not in 4.0
      Validatable validatable,
      // Not before 3.3.0.GA
      JBossTransactionManagerLookup lookup) {
    validatable.validate();
    lookup.getUserTransactionName();
  }
}
