/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package testing;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.net.InetAddress;
import java.net.UnknownHostException;

@AutoService(ResourceProvider.class)
public class TestResourceProvider implements ResourceProvider {

  @Override
  public Resource createResource(ConfigProperties config) {
    // used in test to determine whether this method was called
    System.setProperty("test.resource.provider.called", "true");
    // this call trigger loading InetAddressResolverProvider SPI on jdk 18
    try {
      InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
    return Resource.empty();
  }
}
