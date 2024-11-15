/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
  id("com.gradleup.shadow")
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:jdbc:testing"))
}

tasks {
  shadowJar {
    dependencies {
      // including only current module excludes its transitive dependencies
      include(project(":instrumentation:jdbc:library"))
    }
    // rename classes that are included in :instrumentation:jdbc:bootstrap
    relocate("io.opentelemetry.instrumentation.jdbc.internal.dbinfo", "io.opentelemetry.javaagent.bootstrap.jdbc")
  }

  // this will be included in javaagent module
  val extractShadowJarJavaagent by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow-javaagent")
    exclude("META-INF/**")
    exclude("io/opentelemetry/javaagent/bootstrap/**")
  }

  // this will be included in bootstrap module
  val extractShadowJarBootstrap by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow-bootstrap")
    include("io/opentelemetry/javaagent/bootstrap/**")
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
