/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
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
    Set<String> versions =
        GradleParser.parseMuzzleBlock(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.stream().findFirst().get())
        .isEqualTo("org.elasticsearch.client:rest:[5.0,6.4)");
  }

  @Test
  void testExtractLibraryVersion() {
    String gradleBuildFileContent =
        "dependencies {\n"
            + "  library(\"org.apache.httpcomponents:httpclient:4.3\")\n"
            + "  testImplementation(project(\":instrumentation:apache-httpclient:apache-httpclient-4.3:testing\"))\n"
            + "  latestDepTestLibrary(\"org.apache.httpcomponents:httpclient:4.+\") // see apache-httpclient-5.0 module\n"
            + "}";
    Set<String> versions =
        GradleParser.parseMuzzleBlock(gradleBuildFileContent, InstrumentationType.LIBRARY);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.stream().findFirst().get())
        .isEqualTo("org.apache.httpcomponents:httpclient:4.3");
  }

  @Test
  void testExtractMuzzleVersions_MultiplePassBlocks() {
    String gradleBuildFileContent =
        "plugins {\n"
            + "  id(\"otel.javaagent-instrumentation\")\n"
            + "  id(\"otel.nullaway-conventions\")\n"
            + "  id(\"otel.scala-conventions\")\n"
            + "}\n"
            + "\n"
            + "val zioVersion = \"2.0.0\"\n"
            + "val scalaVersion = \"2.12\"\n"
            + "\n"
            + "muzzle {\n"
            + "  pass {\n"
            + "    group.set(\"dev.zio\")\n"
            + "    module.set(\"zio_2.12\")\n"
            + "    versions.set(\"[$zioVersion,)\")\n"
            + "    assertInverse.set(true)\n"
            + "  }\n"
            + "  pass {\n"
            + "    group.set(\"dev.zio\")\n"
            + "    module.set(\"zio_2.13\")\n"
            + "    versions.set(\"[$zioVersion,)\")\n"
            + "    assertInverse.set(true)\n"
            + "  }\n"
            + "  pass {\n"
            + "    group.set(\"dev.zio\")\n"
            + "    module.set(\"zio_3\")\n"
            + "    versions.set(\"[$zioVersion,)\")\n"
            + "    assertInverse.set(true)\n"
            + "  }\n"
            + "}\n";

    Set<String> versions =
        GradleParser.parseMuzzleBlock(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(versions)
        .containsExactlyInAnyOrder(
            "dev.zio:zio_2.12:[2.0.0,)", "dev.zio:zio_2.13:[2.0.0,)", "dev.zio:zio_3:[2.0.0,)");
  }
}
