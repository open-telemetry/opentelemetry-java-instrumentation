/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;

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
    assertThat(info.versions().size()).isEqualTo(1);
    assertThat(info.versions().stream().findFirst().get())
        .isEqualTo("org.elasticsearch.client:rest:[5.0,6.4)");
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
    assertThat(info.versions().size()).isEqualTo(1);
    assertThat(info.versions().stream().findFirst().get()).isEqualTo("Java 8+");
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
    assertThat(info.versions().size()).isEqualTo(1);
    assertThat(info.minJavaVersionSupported()).isEqualTo(11);
    assertThat(info.versions().stream().findFirst().get()).isEqualTo("Java 11+");
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
    assertThat(info.versions().size()).isEqualTo(1);
    assertThat(info.versions().stream().findFirst().get()).isEqualTo("Java 8+");
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
                name.set("Pre-2.6 network instrumentation")
                group.set("com.couchbase.client")
                module.set("java-client")
                versions.set("[2,2.6)")
                assertInverse.set(true)
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

  @Test
  void testExtractMuzzleVersions_MultiplePassBlocks() {
    String gradleBuildFileContent =
        """
          plugins {
            id("otel.javaagent-instrumentation")
            id("otel.nullaway-conventions")
            id("otel.scala-conventions")
          }

          val zioVersion = "2.0.0"
          val scalaVersion = "2.12"

          muzzle {
            pass {
              group.set("dev.zio")
              module.set("zio_2.12")
              versions.set("[$zioVersion,)")
              assertInverse.set(true)
            }
            pass {
              group.set("dev.zio")
              module.set("zio_2.13")
              versions.set("[$zioVersion,)")
              assertInverse.set(true)
            }
            pass {
              group.set("dev.zio")
              module.set("zio_3")
              versions.set("[$zioVersion,)")
              assertInverse.set(true)
            }
          }
          """;

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.JAVAAGENT);
    assertThat(info.versions())
        .containsExactlyInAnyOrder(
            "dev.zio:zio_2.12:[2.0.0,)", "dev.zio:zio_2.13:[2.0.0,)", "dev.zio:zio_3:[2.0.0,)");
  }
}
