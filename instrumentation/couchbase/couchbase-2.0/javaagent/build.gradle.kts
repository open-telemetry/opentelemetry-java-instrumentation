import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2,3)")
    // these versions were released as ".bundle" instead of ".jar"
    skip("2.7.5", "2.7.8")
    assertInverse.set(true)

    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.Couchbase20NetworkInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.Couchbase26NetworkInstrumentationModule")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directive above is the range we document
    name.set("Pre-2.6 network instrumentation")
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2,2.6)")
    assertInverse.set(true)

    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.Couchbase26NetworkInstrumentationModule")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the first directive is the range we document
    name.set("Couchbase 2.6 network instrumentation")
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2.6.0,3)")
    // these versions were released as ".bundle" instead of ".jar"
    skip("2.7.5", "2.7.8")
    assertInverse.set(true)

    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseInstrumentationModule")
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.Couchbase20NetworkInstrumentationModule")
  }
  fail {
    group.set("com.couchbase.client")
    module.set("couchbase-client")
    versions.set("(,)")
  }
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-common-2.0:javaagent"))
  implementation(project(":instrumentation:rxjava:rxjava-1.0:library"))

  library("com.couchbase.client:java-client:2.0.0")
  compileOnly("com.couchbase.client:core-io:1.6.0")

  testImplementation("com.couchbase.mock:CouchbaseMock:1.5.19")

  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:couchbase:couchbase-3.2:javaagent"))

  latestDepTestLibrary("com.couchbase.client:java-client:2.5.+") // see test suite below
}

testing.suites {
  register<JvmTestSuite>("version25Test") {
    dependencies {
      implementation(project(":instrumentation:couchbase:couchbase-common:testing"))
      implementation("com.couchbase.client:java-client:${baseVersion("2.5.0").orLatest("2.5.+")}")
      implementation(
        "org.springframework.data:spring-data-couchbase:${baseVersion("2.0.0.RELEASE").orLatest("2.+")}"
      )
    }
  }
  register<JvmTestSuite>("version26Test") {
    dependencies {
      implementation(project(":instrumentation:couchbase:couchbase-common:testing"))
      implementation("com.couchbase.client:java-client:${baseVersion("2.6.0").orLatest("2.+")}")
      implementation(
        "org.springframework.data:spring-data-couchbase:${baseVersion("3.1.0.RELEASE").orLatest("3.1.+")}"
      )
      implementation("com.couchbase.client:encryption:${baseVersion("1.0.0").orLatest("1.+")}")
    }
  }

  val common = Action<Test> {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    if (otelProps.denyUnsafe) {
      enabled = false
    }
  }
  val experimental = Action<Test> {
    telemetryConfig("otel.instrumentation.couchbase.emit-experimental-telemetry", "true")
  }

  executions("test", "version26Test") {
    configureEach(common)
    register("Experimental", experimental)
  }
  executions("version25Test") {
    configureEach(common)
    register("Experimental", experimental)
    register("QuerySanitizationDisabled") {
      filter {
        includeTestsMatching("CouchbaseQuerySanitizationTest")
      }
      jvmArgs("-Dotel.instrumentation.couchbase.query-sanitization.enabled=false")
      systemProperty("testQuerySanitizationEnabled", "false")
    }
  }
}

tasks.check {
  dependsOn(testing.suites)
}
