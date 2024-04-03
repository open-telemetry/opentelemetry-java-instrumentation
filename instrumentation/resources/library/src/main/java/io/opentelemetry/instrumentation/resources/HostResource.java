/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Factory for a {@link Resource} which provides information about the host info. */
public final class HostResource {

  private static final Resource INSTANCE = buildResource();

  /** Returns a {@link Resource} which provides information about host. */
  public static Resource get() {
    return INSTANCE;
  }

  // Visible for testing
  static Resource buildResource() {
    AttributesBuilder attributes = Attributes.builder();
    try {
      attributes.put(HostIncubatingAttributes.HOST_NAME, InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException e) {
      // Ignore
    }
    String hostArch = null;
    try {
      hostArch = System.getProperty("os.arch");
    } catch (SecurityException t) {
      // Ignore
    }
    if (hostArch != null) {
      attributes.put(HostIncubatingAttributes.HOST_ARCH, hostArch);
    }

    return Resource.create(attributes.build(), SchemaUrls.V1_24_0);
  }

  private HostResource() {}
}
