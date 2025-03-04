/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GradleParserTest {

  @Test
  void testExtractMuzzleVersions_SinglePassBlock() {
    String gradleBuildFileContent =
        "muzzle {\n"
            + "  pass {\n"
            + "    group.set(\"org.elasticsearch.client\")\n"
            + "    module.set(\"rest\")\n"
            + "    versions.set(\"[5.0,6.4)\")\n"
            + "  }\n"
            + "}";
    List<String> versions = GradleParser.parseMuzzleBlock(gradleBuildFileContent);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.get(0)).isEqualTo("org.elasticsearch.client:rest:[5.0,6.4)");
  }
}
