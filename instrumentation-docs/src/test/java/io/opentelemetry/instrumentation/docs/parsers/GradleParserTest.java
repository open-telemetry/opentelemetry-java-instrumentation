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
  void testExtractLibraryVersion() {
    String gradleBuildFileContent =
        """
            dependencies {
              library("org.apache.httpcomponents:httpclient:4.3")
            }""";
    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.LIBRARY);
    assertThat(info.versions().size()).isEqualTo(1);
    assertThat(info.versions().stream().findFirst().get())
        .isEqualTo("org.apache.httpcomponents:httpclient:4.3");
  }

  @Test
  void testExtractLibraryUpperVersion() {
    String gradleBuildFileContent =
        """
            dependencies {
              library("org.apache.httpcomponents:httpclient:4.3")
              testImplementation(project(":instrumentation:apache-httpclient:apache-httpclient-4.3:testing"))
              latestDepTestLibrary("org.apache.httpcomponents:httpclient:4.+") // see apache-httpclient-5.0 module
            }""";

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.LIBRARY);
    assertThat(info.versions().size()).isEqualTo(1);
    assertThat(info.versions().stream().findFirst().get())
        .isEqualTo("org.apache.httpcomponents:httpclient:[4.3,4.+)");
  }

  @Test
  void testExtractCoreJdk() {
    String gradleBuildFileContent =
        """
            muzzle {
              pass {
                coreJdk()
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
              coreJdk()
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
              coreJdk()
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

  @Test
  void testExtractLogbackLibrary() {
    String gradleBuildFileContent =
        """
          compileOnly("ch.qos.logback:logback-classic") {
            version {
              // compiling against newer version than the earliest supported version (1.0.0) to support
              // features added in 1.3.0
              strictly("1.3.0")
            }
          }
          compileOnly("org.slf4j:slf4j-api") {
            version {
              strictly("2.0.0")
            }
          }
          compileOnly("net.logstash.logback:logstash-logback-encoder") {
            version {
              strictly("3.0")
            }
          }
          """;

    DependencyInfo info =
        GradleParser.parseGradleFile(gradleBuildFileContent, InstrumentationType.LIBRARY);
    assertThat(info.versions())
        .containsExactlyInAnyOrder(
            "ch.qos.logback:logback-classic:1.3.0",
            "org.slf4j:slf4j-api:2.0.0",
            "net.logstash.logback:logstash-logback-encoder:3.0");
  }
}
