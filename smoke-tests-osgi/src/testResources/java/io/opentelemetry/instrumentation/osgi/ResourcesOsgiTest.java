/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.osgi;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.junit5.context.BundleContextExtension;

@ExtendWith(BundleContextExtension.class)
class ResourcesOsgiTest {

  @InjectBundleContext BundleContext bundleContext;

  // The resources module declares ResourceProvider implementations via AutoService-generated
  // META-INF/services entries. bnd's "-metainf-services: auto" turns those into osgi.serviceloader
  // Provide-Capability headers, which the Aries SPI Fly registrar bundle reads to publish the
  // providers as OSGi services. Finding them in the service registry proves the chain works.
  @Test
  void resourceProvidersAreRegisteredViaSpiFly() throws Exception {
    Collection<ServiceReference<ResourceProvider>> refs =
        bundleContext.getServiceReferences(ResourceProvider.class, null);
    assertFalse(refs.isEmpty());
  }
}
