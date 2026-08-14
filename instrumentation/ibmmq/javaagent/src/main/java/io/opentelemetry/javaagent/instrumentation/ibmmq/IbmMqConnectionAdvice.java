/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static io.opentelemetry.javaagent.instrumentation.ibmmq.IbmMqSingletons.queueManagerIdVirtualField;

import com.ibm.mq.MQQueueManager;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;

public class IbmMqConnectionAdvice {

  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void onExit(@Advice.This MQQueueManager queueManager) {
    if (queueManager == null) {
      return;
    }

    VirtualField<MQQueueManager, String> qmIdField = queueManagerIdVirtualField();

    String cachedQmId = qmIdField.get(queueManager);
    if (cachedQmId != null) {
      return;
    }

    String qmId = getQueueManagerId(queueManager);
    if (qmId != null) {
      qmIdField.set(queueManager, qmId);
    }
  }

  @Nullable
  private static String getQueueManagerId(Object queueManager) {
    try {
      // MQCA_Q_MGR_IDENTIFIER (CMQC.MQCA_Q_MGR_IDENTIFIER == 2032). Note 2016 is
      // MQCA_Q_NAME, which is not valid for a queue manager inquiry.
      int[] selectors = {2032};
      int[] intAttrs = new int[0];
      byte[] charAttrs = new byte[48];

      Method inquireMethod =
          queueManager.getClass().getMethod("inquire", int[].class, int[].class, byte[].class);
      inquireMethod.invoke(queueManager, selectors, intAttrs, charAttrs);

      String qmId = new String(charAttrs, StandardCharsets.UTF_8).trim();
      return qmId.isEmpty() ? null : qmId;
    } catch (Throwable t) {
      return null;
    }
  }
}
