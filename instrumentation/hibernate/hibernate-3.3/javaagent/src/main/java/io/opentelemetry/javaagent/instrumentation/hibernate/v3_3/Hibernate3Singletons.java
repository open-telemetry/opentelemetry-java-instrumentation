/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateOperation;

public class Hibernate3Singletons {

  private static final Instrumenter<HibernateOperation, Void> INSTANCE =
      HibernateInstrumenterFactory.createInstrumenter("io.opentelemetry.hibernate-3.3");

  public static Instrumenter<HibernateOperation, Void> instrumenter() {
    return INSTANCE;
  }

  private Hibernate3Singletons() {}
}
