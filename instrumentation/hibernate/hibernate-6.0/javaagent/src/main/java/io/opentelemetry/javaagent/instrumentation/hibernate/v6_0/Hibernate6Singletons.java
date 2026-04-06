/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateOperation;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionInfo;
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;
import org.hibernate.query.CommonQueryContract;

public class Hibernate6Singletons {

  private static final Instrumenter<HibernateOperation, Void> INSTANCE =
      HibernateInstrumenterFactory.createInstrumenter("io.opentelemetry.hibernate-6.0");

  public static final VirtualField<CommonQueryContract, SessionInfo>
      COMMON_QUERY_CONTRACT_SESSION_INFO =
          VirtualField.find(CommonQueryContract.class, SessionInfo.class);

  public static final VirtualField<SharedSessionContract, SessionInfo>
      SHARED_SESSION_CONTRACT_SESSION_INFO =
          VirtualField.find(SharedSessionContract.class, SessionInfo.class);

  public static final VirtualField<Transaction, SessionInfo> TRANSACTION_SESSION_INFO =
      VirtualField.find(Transaction.class, SessionInfo.class);

  public static Instrumenter<HibernateOperation, Void> instrumenter() {
    return INSTANCE;
  }

  private Hibernate6Singletons() {}
}
