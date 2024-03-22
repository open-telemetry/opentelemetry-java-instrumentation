/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

public class ResourceProviderTest {

  @Test
  void resourceProviderOrder() throws Exception {
    boolean containerProviderFound = false;
    boolean awsProviderFound = false;
    // verify that aws resource provider is found after the regular container provider
    // provider that is found later can overrider values from previous providers
    Class<?> resourceProviderClass =
        Class.forName(
            "io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider",
            false,
            IntegrationTestUtils.getAgentClassLoader());
    for (Object resourceProvider :
        ServiceLoader.load(resourceProviderClass, IntegrationTestUtils.getAgentClassLoader())) {
      Class<?> clazz = resourceProvider.getClass();
      if (clazz
          .getName()
          .equals("io.opentelemetry.instrumentation.resources.ContainerResourceProvider")) {
        containerProviderFound = true;
        assertFalse(awsProviderFound);
      } else if (clazz.getName().startsWith("io.opentelemetry.contrib.aws.resource.")) {
        awsProviderFound = true;
        assertTrue(containerProviderFound);
      }
    }
    assertTrue(containerProviderFound);
    assertTrue(awsProviderFound);
  }
}
