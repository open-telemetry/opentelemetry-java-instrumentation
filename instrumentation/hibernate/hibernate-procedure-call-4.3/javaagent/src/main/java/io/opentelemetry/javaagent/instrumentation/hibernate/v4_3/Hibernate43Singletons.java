/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_3;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateOperation;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionInfo;
import org.hibernate.SharedSessionContract;
import org.hibernate.procedure.ProcedureCall;

public final class Hibernate43Singletons {

  private static final Instrumenter<HibernateOperation, Void> INSTANCE =
      HibernateInstrumenterFactory.createInstrumenter(
          "io.opentelemetry.hibernate-procedure-call-4.3");

  public static final VirtualField<ProcedureCall, SessionInfo> PROCEDURE_CALL_SESSION_INFO =
      VirtualField.find(ProcedureCall.class, SessionInfo.class);
  public static final VirtualField<SharedSessionContract, SessionInfo>
      SHARED_SESSION_CONTRACT_SESSION_INFO =
          VirtualField.find(SharedSessionContract.class, SessionInfo.class);

  public static Instrumenter<HibernateOperation, Void> instrumenter() {
    return INSTANCE;
  }

  private Hibernate43Singletons() {}
}
