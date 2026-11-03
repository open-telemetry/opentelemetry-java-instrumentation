import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    group.set("org.influxdb")
    module.set("influxdb-java")
    versions.set("[2.4,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.influxdb:influxdb-java:2.4")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  // we use methods that weren't present before 2.14 in tests
  testLibrary("org.influxdb:influxdb-java:2.14")
}

val test225RuntimeClasspath = configurations.create("test225RuntimeClasspath") {
  extendsFrom(configurations.testRuntimeClasspath.get())
  resolutionStrategy.force("org.influxdb:influxdb-java:2.25")
}
val test213RuntimeClasspath = configurations.create("test213RuntimeClasspath") {
  extendsFrom(configurations.testRuntimeClasspath.get())
  resolutionStrategy.force("org.influxdb:influxdb-java:2.13")
}
val test29RuntimeClasspath = configurations.create("test29RuntimeClasspath") {
  extendsFrom(configurations.testRuntimeClasspath.get())
  resolutionStrategy.force("org.influxdb:influxdb-java:2.9")
}

testing.suites {
  register<JvmTestSuite>("unitTests") {
    dependencies {
      implementation(project())
      implementation("org.influxdb:influxdb-java:2.4")
    }
  }

  register<JvmTestSuite>("test24") {
    dependencies {
      implementation(project())
      implementation("org.influxdb:influxdb-java:2.4")
      implementation("org.testcontainers:testcontainers")
    }
  }

  executions("test") {
    configureDefault {
      filter {
        excludeTestsMatching("InfluxDbQuerySanitizationDisabledTest")
      }
    }

    register("QuerySanitizationDisabled") {
      filter {
        includeTestsMatching("InfluxDbQuerySanitizationDisabledTest")
      }
      telemetryConfig("otel.instrumentation.influxdb.query-sanitization.enabled", "false")
    }

    register("225") {
      classpath = sourceSets.test.get().output + test225RuntimeClasspath
      systemProperty("influxdb.test.client-version", "2.25")
      filter {
        includeTestsMatching("InfluxDbQueryLifecycleTest")
      }
    }

    register("213") {
      classpath = sourceSets.test.get().output + test213RuntimeClasspath
      include("**/InfluxDbLegacyQueryLifecycleTest.class")
      filter {
        includeTestsMatching("*InfluxDbLegacyQueryLifecycleTest.sameQueryReentrantAsyncQueryDuringChunkedEncodingDoesNotEndOuterQuery")
      }
    }

    register("29") {
      classpath = sourceSets.test.get().output + test29RuntimeClasspath
      include("**/InfluxDbLegacyQueryLifecycleTest.class")
      filter {
        includeTestsMatching("*InfluxDbLegacyQueryLifecycleTest.sameQueryReentrantAsyncQueryDuringChunkedEncodingDoesNotEndOuterQuery")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // we disable the okhttp instrumentation, so we don't need to assert on the okhttp spans
    // from the okhttp instrumentation we need OkHttp3IgnoredTypesConfigurer to fix context leaks
    jvmArgs("-Dotel.instrumentation.okhttp.enabled=false")
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  check {
    dependsOn(testing.suites)
  }
}
