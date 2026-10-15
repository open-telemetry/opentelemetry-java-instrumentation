/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 *
 * @deprecated Use {@link SystemProperty}. This class will be removed in 3.0.
 */
@Deprecated
public final class ConfigPropertiesUtil {

  /**
   * @deprecated Use {@link SystemProperty#getBoolean(String, boolean)}.
   */
  @Deprecated
  public static boolean getBoolean(String propertyName, boolean defaultValue) {
    return SystemProperty.getBoolean(propertyName, defaultValue);
  }

  /**
   * @deprecated Use {@link SystemProperty#getBoolean(String)}.
   */
  @Deprecated
  @Nullable
  public static Boolean getBoolean(String propertyName) {
    return SystemProperty.getBoolean(propertyName);
  }

  /**
   * @deprecated Use {@link SystemProperty#getInt(String, int)}.
   */
  @Deprecated
  public static int getInt(String propertyName, int defaultValue) {
    return SystemProperty.getInt(propertyName, defaultValue);
  }

  /**
   * @deprecated Use {@link SystemProperty#getString(String)}.
   */
  @Deprecated
  @Nullable
  public static String getString(String propertyName) {
    return SystemProperty.getString(propertyName);
  }

  /**
   * @deprecated Use {@link SystemProperty#getString(String, String)}.
   */
  @Deprecated
  public static String getString(String propertyName, String defaultValue) {
    return SystemProperty.getString(propertyName, defaultValue);
  }

  /**
   * @deprecated Use {@link SystemProperty#getList(String, List)}.
   */
  @Deprecated
  public static List<String> getList(String propertyName, List<String> defaultValue) {
    return SystemProperty.getList(propertyName, defaultValue);
  }

  private ConfigPropertiesUtil() {}
}
