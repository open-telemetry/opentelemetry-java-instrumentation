/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateOperation;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionInfo;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Transaction;

public class Hibernate3Singletons {

  private static final Instrumenter<HibernateOperation, Void> INSTANCE =
      HibernateInstrumenterFactory.createInstrumenter("io.opentelemetry.hibernate-3.3");

  public static final VirtualField<Criteria, SessionInfo> CRITERIA_SESSION_INFO =
      VirtualField.find(Criteria.class, SessionInfo.class);

  public static final VirtualField<Query, SessionInfo> QUERY_SESSION_INFO =
      VirtualField.find(Query.class, SessionInfo.class);

  public static final VirtualField<Transaction, SessionInfo> TRANSACTION_SESSION_INFO =
      VirtualField.find(Transaction.class, SessionInfo.class);

  public static Instrumenter<HibernateOperation, Void> instrumenter() {
    return INSTANCE;
  }

  private Hibernate3Singletons() {}
}
