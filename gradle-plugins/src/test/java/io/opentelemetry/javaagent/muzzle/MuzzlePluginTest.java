/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.eclipse.aether.version.Version;
import org.junit.jupiter.api.Test;

class MuzzlePluginTest {

  @Test
  void rangeRequest() {
    AcceptableVersions predicate = new AcceptableVersions(Collections.emptyList());

    assertThat(predicate.test(new TestVersion("10.1.0-rc2+19-8e20bb26"))).isFalse();
    assertThat(predicate.test(new TestVersion("2.4.5.BUILD-SNAPSHOT"))).isFalse();
  }

  static class TestVersion implements Version {

    private final String version;

    TestVersion(String version) {
      this.version = version;
    }

    @Override
    public int compareTo(Version o) {
      return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
      return version;
    }
  }
}
