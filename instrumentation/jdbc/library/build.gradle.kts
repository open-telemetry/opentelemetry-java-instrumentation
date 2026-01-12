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

  testLibrary("com.h2database:h2:1.3.169")
  testLibrary("org.apache.derby:derby:10.6.1.0")
  testLibrary("org.hsqldb:hsqldb:2.0.0")

  testLibrary("org.apache.tomcat:tomcat-jdbc:7.0.19")
  testLibrary("org.apache.tomcat:tomcat-juli:7.0.19") // tomcat jdbc needs this
  testLibrary("com.zaxxer:HikariCP:2.4.0")
  testLibrary("com.mchange:c3p0:0.9.5")

  // some classes in earlier versions of derby were split out into derbytools in later versions
  latestDepTestLibrary("org.apache.derby:derbytools:latest.release")
}

tasks {
  // We cannot use "--release" javac option here because that will forbid using apis that were added
  // in later versions. In JDBC wrappers we wish to implement delegation for methods that are not
  // present in jdk8.
  compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.release.set(null as Int?)
  }

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
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    jvmArgs("-Dotel.instrumentation.jdbc.experimental.transaction.enabled=true")
  }
}
