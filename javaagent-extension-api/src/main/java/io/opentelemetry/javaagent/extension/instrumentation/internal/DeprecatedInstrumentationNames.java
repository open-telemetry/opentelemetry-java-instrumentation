/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static java.util.Collections.singletonList;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Helper for {@code InstrumentationModule}s whose names have been renamed: expands names containing
 * the inline marker {@code "<current>|deprecated:<old>"} into the current name followed by the
 * deprecated name.
 *
 * <p>Under {@code otel.instrumentation.common.v3-preview=true} the deprecated name is dropped, so
 * only the current name is registered. Otherwise, if the deprecated name has been explicitly set
 * via {@code otel.instrumentation.<old>.enabled} (flat properties or YAML), a warning is logged
 * pointing users at the current name.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DeprecatedInstrumentationNames {

  private static final Logger logger =
      Logger.getLogger(DeprecatedInstrumentationNames.class.getName());

  private static final String DEPRECATED_MARKER = "|deprecated:";

  /**
   * Expands names containing {@code "<current>|deprecated:<old>"} into the current name followed by
   * the deprecated name. Names without the marker are returned unchanged.
   *
   * <p>Under {@code otel.instrumentation.common.v3-preview=true} the deprecated name is dropped.
   * Otherwise, if the deprecated name has been explicitly set via {@code
   * otel.instrumentation.<old>.enabled} (flat properties or YAML), a warning is logged pointing
   * users at the current name.
   */
  public static String[] expandDeprecatedNames(String... names) {
    boolean hasMarker = false;
    for (String name : names) {
      if (name.contains(DEPRECATED_MARKER)) {
        hasMarker = true;
        break;
      }
    }
    if (!hasMarker) {
      return names;
    }

    boolean v3Preview = AgentCommonConfig.get().isV3Preview();
    AgentDistributionConfig config = AgentDistributionConfig.get();
    List<String> expanded = new ArrayList<>(names.length + 1);
    for (String name : names) {
      int idx = name.indexOf(DEPRECATED_MARKER);
      if (idx < 0) {
        expanded.add(name);
        continue;
      }
      String current = name.substring(0, idx);
      String deprecated = name.substring(idx + DEPRECATED_MARKER.length());
      expanded.add(current);
      if (v3Preview) {
        continue;
      }
      expanded.add(deprecated);
      List<String> probe = singletonList(deprecated);
      if (config.isInstrumentationEnabled(probe, true)
          != config.isInstrumentationEnabled(probe, false)) {
        logger.log(
            WARNING,
            "otel.instrumentation.{0}.enabled is deprecated; "
                + "use otel.instrumentation.{1}.enabled instead.",
            new Object[] {deprecated, current});
      }
    }
    return expanded.toArray(new String[0]);
  }

  private DeprecatedInstrumentationNames() {}
}
