/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstrumentationNameComparator {

  private InstrumentationNameComparator() {}

  /**
   * Sort by base name, then optional version suffix (major[.minor[.patch]]). For the same base
   * name, unversioned comes before versioned.
   */
  static final Comparator<InstrumentationModule> BY_NAME_AND_VERSION =
      Comparator.comparing(
          (InstrumentationModule m) -> Key.parse(m.getInstrumentationName()),
          Key.KeyComparator.INSTANCE);

  private record Key(
      String baseLower,
      boolean hasVersion,
      int major,
      boolean hasMinor,
      int minor,
      boolean hasPatch,
      int patch) {
    private static final Pattern PATTERN =
        Pattern.compile("^(.+?)[-._](\\d+)(?:[._](\\d+))?(?:[._](\\d+))?$");

    static Key parse(String name) {
      if (name == null) {
        return new Key("", false, 0, false, 0, false, 0);
      }

      Matcher m = PATTERN.matcher(name);
      if (!m.matches()) {
        return new Key(name.toLowerCase(Locale.ROOT), false, 0, false, 0, false, 0);
      }

      String base = m.group(1);
      String majorStr = m.group(2);
      String minorStr = m.group(3);
      String patchStr = m.group(4);

      if (majorStr == null) {
        return new Key(base.toLowerCase(Locale.ROOT), false, 0, false, 0, false, 0);
      }

      int major = Integer.parseInt(majorStr);
      boolean hasMinor = minorStr != null;
      int minor = hasMinor ? Integer.parseInt(minorStr) : 0;
      boolean hasPatch = patchStr != null;
      int patch = hasPatch ? Integer.parseInt(patchStr) : 0;

      return new Key(base.toLowerCase(Locale.ROOT), true, major, hasMinor, minor, hasPatch, patch);
    }

    private static final class KeyComparator implements Comparator<Key> {
      static final KeyComparator INSTANCE = new KeyComparator();

      @Override
      public int compare(Key k1, Key k2) {
        int compare = k1.baseLower.compareTo(k2.baseLower);
        if (compare != 0) {
          return compare;
        }

        if (k1.hasVersion != k2.hasVersion) {
          return k1.hasVersion ? 1 : -1;
        }
        if (!k1.hasVersion) {
          return 0;
        }

        compare = Integer.compare(k1.major, k2.major);
        if (compare != 0) {
          return compare;
        }

        // Treat "1" as "1.0" if compared to "1.0"
        int k1Minor = k1.hasMinor ? k1.minor : 0;
        int k2Minor = k2.hasMinor ? k2.minor : 0;
        compare = Integer.compare(k1Minor, k2Minor);
        if (compare != 0) {
          return compare;
        }

        // Treat "1.0" as "1.0.0" if compared to "1.0.0"
        int k1Patch = k1.hasPatch ? k1.patch : 0;
        int k2Patch = k2.hasPatch ? k2.patch : 0;
        return Integer.compare(k1Patch, k2Patch);
      }
    }
  }
}
