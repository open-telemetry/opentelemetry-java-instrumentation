/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.muzzle;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.eclipse.aether.version.Version;

class AcceptableVersions implements Predicate<Version> {
  private static final Pattern GIT_SHA_PATTERN = Pattern.compile("^.*-[0-9a-f]{7,}$");

  private final Collection<String> skipVersions;

  AcceptableVersions(Collection<String> skipVersions) {
    this.skipVersions = skipVersions;
  }

  @Override
  public boolean test(Version version) {
    if (version == null) {
      return false;
    }
    String versionString = version.toString().toLowerCase(Locale.ROOT);
    if (skipVersions.contains(versionString)) {
      return false;
    }

    boolean draftVersion =
        versionString.contains("rc")
            || versionString.contains(".cr")
            || versionString.contains("alpha")
            || versionString.contains("beta")
            || versionString.contains("-b")
            || versionString.contains(".m")
            || versionString.contains("-m")
            || versionString.contains("-dev")
            || versionString.contains("-ea")
            || versionString.contains("-atlassian-")
            || versionString.contains("public_draft")
            || versionString.contains("snapshot")
            || GIT_SHA_PATTERN.matcher(versionString).matches();

    return !draftVersion;
  }
}
