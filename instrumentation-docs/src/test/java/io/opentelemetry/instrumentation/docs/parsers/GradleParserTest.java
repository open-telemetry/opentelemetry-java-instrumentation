/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.docs.internal.DependencyInfo;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import org.junit.jupiter.api.Test;

class GradleParserTest {

  @Test
  void testExtractMuzzleVersions_SinglePassBlock() {
    String gradleBuildFileContent =
        """
            muzzle {
              pass {
                group.set("org.elasticsearch.client")
                module.set("rest")
                versions.set("[5.0,6.4)")
              }
            }""";
    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.versions()).containsExactly("org.elasticsearch.client:rest:[5.0,6.4)");
  }

  @Test
  void testExtractMuzzleVersions_PlayCoordinates() {
    String gradleBuildFileContent =
        """
            muzzle {
              pass {
                group.set("com.typesafe.play")
                module.set("play_2.11")
                versions.set("[2.6.0,)")
                assertInverse.set(true)
              }
              pass {
                group.set("org.playframework")
                module.set("play_3")
                versions.set("(,)")
                assertInverse.set(true)
              }
            }""";

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.versions())
        .containsExactlyInAnyOrder(
            "com.typesafe.play:play_2.11:[2.6.0,)", "org.playframework:play_3:(,)");
  }

  @Test
  void testRejectsNonLiteralMuzzleFields() {
    assertThatThrownBy(
            () ->
                GradleParser.parseGradleFile(
                    "muzzle { pass { group.set(playGroup) } }", InstrumentationType.JAVAAGENT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must use a literal string");

    assertThatThrownBy(
            () ->
                GradleParser.parseGradleFile(
                    "muzzle { pass { module.set(\"$playModule\") } }",
                    InstrumentationType.JAVAAGENT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must use a literal string");

    assertThatThrownBy(
            () ->
                GradleParser.parseGradleFile(
                    "muzzle { pass { versions.set(\"${versionRange}\") } }",
                    InstrumentationType.JAVAAGENT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must use a literal string");
  }

  @Test
  void testExtractCoreJdk() {
    String gradleBuildFileContent =
        """
            muzzle {
              pass {
                coreJdk.set(true)
              }
            }
            """;

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.versions()).containsExactly("Java 8+");
  }

  @Test
  void testExtractMinimumJavaVersion() {
    String gradleBuildFileContent =
        """
          muzzle {
            pass {
              coreJdk.set(true)
            }
          }

          otelJava {
            minJavaVersionSupported.set(JavaVersion.VERSION_11)
          }
          """;

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.minJavaVersionSupported()).isEqualTo(11);
    assertThat(info.versions()).containsExactly("Java 11+");
  }

  @Test
  void testExtractMinimumJavaVersionIgnoredWithinIfCondition() {
    String gradleBuildFileContent =
        """
          muzzle {
            pass {
              coreJdk.set(true)
            }
          }

          if (latestDepTest) {
            otelJava {
              minJavaVersionSupported.set(JavaVersion.VERSION_11)
            }
          }
          """;

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.versions()).containsExactly("Java 8+");
  }

  @Test
  void testDocsIgnoreSkipsPassBlock() {
    String gradleBuildFileContent =
        """
            muzzle {
              pass {
                group.set("com.couchbase.client")
                module.set("java-client")
                versions.set("[2,3)")
                assertInverse.set(true)
              }
              pass {
                // instrumentation-docs:ignore - verification only
                group.set(playGroup)
                module.set("play_${name}")
                versions.set("${versionRange}")
              }
              pass {
                group.set("com.couchbase.client")
                module.set("java-client")
                versions.set("[3,4)")
                assertInverse.set(true)
              }
            }""";

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.versions())
        .containsExactlyInAnyOrder(
            "com.couchbase.client:java-client:[2,3)", "com.couchbase.client:java-client:[3,4)");
  }

  @Test
  void testDocsIgnoreSkipsCoreJdkPassBlock() {
    String gradleBuildFileContent =
        """
            muzzle {
              pass {
                // instrumentation-docs:ignore
                coreJdk.set(true)
              }
            }""";

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.versions()).isEmpty();
  }

  @Test
  void testDocsIgnoreAbovePassBlockIsNotHonored() {
    String gradleBuildFileContent =
        """
            muzzle {
              // instrumentation-docs:ignore
              pass {
                group.set("com.couchbase.client")
                module.set("java-client")
                versions.set("[2,2.6)")
              }
            }""";

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.versions()).containsExactly("com.couchbase.client:java-client:[2,2.6)");
  }
}
