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
import java.util.Locale;
import javax.annotation.Nullable;

/** Factory of a {@link Resource} which provides information about the current operating system. */
public final class OsResource {

  // copied from OsIncubatingAttributes
  private static final AttributeKey<String> OS_DESCRIPTION =
      AttributeKey.stringKey("os.description");
  private static final AttributeKey<String> OS_TYPE = AttributeKey.stringKey("os.type");

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
      attributes.put(OS_TYPE, osName);
    }

    String version = null;
    try {
      version = System.getProperty("os.version");
    } catch (SecurityException e) {
      // Ignore
    }
    String osDescription = version != null ? os + ' ' + version : os;
    attributes.put(OS_DESCRIPTION, osDescription);

    return Resource.create(attributes.build(), SchemaUrls.V1_24_0);
  }

  @Nullable
  private static String getOs(String os) {
    os = os.toLowerCase(Locale.ROOT);
    if (os.startsWith("windows")) {
      return OsTypeValues.WINDOWS;
    } else if (os.startsWith("linux")) {
      return OsTypeValues.LINUX;
    } else if (os.startsWith("mac")) {
      return OsTypeValues.DARWIN;
    } else if (os.startsWith("freebsd")) {
      return OsTypeValues.FREEBSD;
    } else if (os.startsWith("netbsd")) {
      return OsTypeValues.NETBSD;
    } else if (os.startsWith("openbsd")) {
      return OsTypeValues.OPENBSD;
    } else if (os.startsWith("dragonflybsd")) {
      return OsTypeValues.DRAGONFLYBSD;
    } else if (os.startsWith("hp-ux")) {
      return OsTypeValues.HPUX;
    } else if (os.startsWith("aix")) {
      return OsTypeValues.AIX;
    } else if (os.startsWith("solaris")) {
      return OsTypeValues.SOLARIS;
    } else if (os.startsWith("z/os")) {
      return OsTypeValues.Z_OS;
    }
    return null;
  }

  private OsResource() {}

  // copied from OsIncubatingAttributes
  private static final class OsTypeValues {
    static final String WINDOWS = "windows";
    static final String LINUX = "linux";
    static final String DARWIN = "darwin";
    static final String FREEBSD = "freebsd";
    static final String NETBSD = "netbsd";
    static final String OPENBSD = "openbsd";
    static final String DRAGONFLYBSD = "dragonflybsd";
    static final String HPUX = "hpux";
    static final String AIX = "aix";
    static final String SOLARIS = "solaris";
    static final String Z_OS = "z_os";

    private OsTypeValues() {}
  }
}
