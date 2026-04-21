/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal.cache.weaklockfree;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

class ThreadUtilTest {

  @Test
  @EnabledOnJre(JRE.JAVA_21)
  void isVirtualThread() throws Exception {
    assertThat(ThreadUtil.isVirtualThread(new Thread())).isFalse();
    Object builder = Thread.class.getMethod("ofVirtual").invoke(null);
    Thread thread =
        (Thread)
            Class.forName("java.lang.Thread$Builder$OfVirtual")
                .getMethod("unstarted", Runnable.class)
                .invoke(builder, (Runnable) () -> {});
    assertThat(ThreadUtil.isVirtualThread(thread)).isTrue();
  }
}
