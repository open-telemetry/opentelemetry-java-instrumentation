/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Factory for a {@link Resource} which provides information about the host info. */
public final class HostResource {

  // copied from HostIncubatingAttributes
  private static final AttributeKey<String> HOST_ARCH = AttributeKey.stringKey("host.arch");
  private static final AttributeKey<String> HOST_NAME = AttributeKey.stringKey("host.name");

  private static final Resource INSTANCE = buildResource();

  /** Returns a {@link Resource} which provides information about host. */
  public static Resource get() {
    return INSTANCE;
  }

  // Visible for testing
  static Resource buildResource() {
    AttributesBuilder attributes = Attributes.builder();
    try {
      attributes.put(HOST_NAME, InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException ignored) {
      // Ignore
    }
    String osArch = null;
    try {
      osArch = System.getProperty("os.arch");
    } catch (SecurityException ignored) {
      // Ignore
    }
    if (osArch != null) {
      attributes.put(HOST_ARCH, toHostArch(osArch));
    }

    return Resource.create(attributes.build(), SchemaUrls.V1_40_0);
  }

  // visible for testing
  static String toHostArch(String osArch) {
    switch (osArch) {
      case "x86_64":
        return HostArchValues.AMD64;
      case "aarch64":
        return HostArchValues.ARM64;
      case "arm":
        return HostArchValues.ARM32;
      case "i386":
        return HostArchValues.X86;
      case "ppc":
        return HostArchValues.PPC32;
      case "ppc64le":
        return HostArchValues.PPC64;
      default:
        // values such as amd64, ppc64 and s390x already match, and a value that has no well-known
        // host.arch value is reported as it is
        return osArch;
    }
  }

  private HostResource() {}

  // copied from HostIncubatingAttributes
  private static final class HostArchValues {
    static final String AMD64 = "amd64";
    static final String ARM32 = "arm32";
    static final String ARM64 = "arm64";
    static final String PPC32 = "ppc32";
    static final String PPC64 = "ppc64";
    static final String X86 = "x86";

    private HostArchValues() {}
  }
}
