/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes;
import java.util.Locale;
import javax.annotation.Nullable;

/** Factory of a {@link Resource} which provides information about the current operating system. */
public final class OsResource {

  private static final Resource INSTANCE = buildResource();

  /**
   * Returns a factory for a {@link Resource} which provides information about the current operating
   * system.
   */
  public static Resource get() {
    return INSTANCE;
  }

  // Visible for testing
  static Resource buildResource() {

    String os;
    try {
      os = System.getProperty("os.name");
    } catch (SecurityException t) {
      // Security manager enabled, can't provide much os information.
      return Resource.empty();
    }

    if (os == null) {
      return Resource.empty();
    }

    AttributesBuilder attributes = Attributes.builder();

    String osName = getOs(os);
    if (osName != null) {
      attributes.put(OsIncubatingAttributes.OS_TYPE, osName);
    }

    String version = null;
    try {
      version = System.getProperty("os.version");
    } catch (SecurityException e) {
      // Ignore
    }
    String osDescription = version != null ? os + ' ' + version : os;
    attributes.put(OsIncubatingAttributes.OS_DESCRIPTION, osDescription);

    return Resource.create(attributes.build(), SchemaUrls.V1_24_0);
  }

  @Nullable
  private static String getOs(String os) {
    os = os.toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      return OsIncubatingAttributes.OsTypeValues.WINDOWS;
    } else if (os.startsWith("linux")) {
      return OsIncubatingAttributes.OsTypeValues.LINUX;
    } else if (os.startsWith("mac")) {
      return OsIncubatingAttributes.OsTypeValues.DARWIN;
    } else if (os.startsWith("freebsd")) {
      return OsIncubatingAttributes.OsTypeValues.FREEBSD;
    } else if (os.startsWith("netbsd")) {
      return OsIncubatingAttributes.OsTypeValues.NETBSD;
    } else if (os.startsWith("openbsd")) {
      return OsIncubatingAttributes.OsTypeValues.OPENBSD;
    } else if (os.startsWith("dragonflybsd")) {
      return OsIncubatingAttributes.OsTypeValues.DRAGONFLYBSD;
    } else if (os.startsWith("hp-ux")) {
      return OsIncubatingAttributes.OsTypeValues.HPUX;
    } else if (os.startsWith("aix")) {
      return OsIncubatingAttributes.OsTypeValues.AIX;
    } else if (os.startsWith("solaris")) {
      return OsIncubatingAttributes.OsTypeValues.SOLARIS;
    } else if (os.startsWith("z/os")) {
      return OsIncubatingAttributes.OsTypeValues.Z_OS;
    }
    return null;
  }

  private OsResource() {}
}
