/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class HibernateExperimentalAttributesExtractor
    implements AttributesExtractor<HibernateOperation, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, HibernateOperation hibernateOperation) {
    String sessionId = hibernateOperation.getSessionId();
    if (sessionId != null) {
      attributes.put("hibernate.session_id", sessionId);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      HibernateOperation hibernateOperation,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
