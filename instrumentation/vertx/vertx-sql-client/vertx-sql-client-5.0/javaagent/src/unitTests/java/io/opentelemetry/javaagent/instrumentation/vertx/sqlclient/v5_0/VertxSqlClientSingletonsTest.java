/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VertxSqlClientSingletonsTest {
  private static boolean initialized;

  @Test
  void loadsVersionedClassWithoutInitializingIt() {
    assertThat(initialized).isFalse();

    Class<?> loadedClass =
        VertxSqlClientSingletons.loadVersionedClass(
            FirstCarrier.class.getName(), "missing.SecondCarrier");

    assertThat(loadedClass).isSameAs(FirstCarrier.class);
    assertThat(initialized).isFalse();

    loadedClass =
        VertxSqlClientSingletons.loadVersionedClass(
            "missing.FirstCarrier", SecondCarrier.class.getName());

    assertThat(loadedClass).isSameAs(SecondCarrier.class);
    assertThat(initialized).isFalse();
  }

  private static class FirstCarrier {
    static {
      initialized = true;
    }
  }

  private static class SecondCarrier {
    static {
      initialized = true;
    }
  }
}
