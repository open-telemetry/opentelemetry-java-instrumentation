/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import java.io.File;
import java.net.URL;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.Test;

class UnsafeTest {

  @Test
  void testGenerateSunMiscUnsafe() throws Exception {
    ByteBuddyAgent.install();
    URL testJarLocation =
        AgentClassLoader.class.getProtectionDomain().getCodeSource().getLocation();

    try (AgentClassLoader loader = new AgentClassLoader(new File(testJarLocation.toURI()))) {
      UnsafeInitializer.initialize(ByteBuddyAgent.getInstrumentation(), loader, false);

      Class<?> unsafeClass = loader.loadClass("sun.misc.Unsafe");

      assertThat(unsafeClass.getClassLoader()).isEqualTo(loader);
    }
  }
}
