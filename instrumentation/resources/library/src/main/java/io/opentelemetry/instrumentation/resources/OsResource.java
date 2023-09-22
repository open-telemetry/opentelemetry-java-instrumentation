/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
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
      attributes.put(ResourceAttributes.OS_TYPE, osName);
    }

    String version = null;
    try {
      version = System.getProperty("os.version");
    } catch (SecurityException e) {
      // Ignore
    }
    String osDescription = version != null ? os + ' ' + version : os;
    attributes.put(ResourceAttributes.OS_DESCRIPTION, osDescription);

    return Resource.create(attributes.build(), ResourceAttributes.SCHEMA_URL);
  }

  @Nullable
  private static String getOs(String os) {
    os = os.toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      return ResourceAttributes.OsTypeValues.WINDOWS;
    } else if (os.startsWith("linux")) {
      return ResourceAttributes.OsTypeValues.LINUX;
    } else if (os.startsWith("mac")) {
      return ResourceAttributes.OsTypeValues.DARWIN;
    } else if (os.startsWith("freebsd")) {
      return ResourceAttributes.OsTypeValues.FREEBSD;
    } else if (os.startsWith("netbsd")) {
      return ResourceAttributes.OsTypeValues.NETBSD;
    } else if (os.startsWith("openbsd")) {
      return ResourceAttributes.OsTypeValues.OPENBSD;
    } else if (os.startsWith("dragonflybsd")) {
      return ResourceAttributes.OsTypeValues.DRAGONFLYBSD;
    } else if (os.startsWith("hp-ux")) {
      return ResourceAttributes.OsTypeValues.HPUX;
    } else if (os.startsWith("aix")) {
      return ResourceAttributes.OsTypeValues.AIX;
    } else if (os.startsWith("solaris")) {
      return ResourceAttributes.OsTypeValues.SOLARIS;
    } else if (os.startsWith("z/os")) {
      return ResourceAttributes.OsTypeValues.Z_OS;
    }
    return null;
  }

  private OsResource() {}
}
