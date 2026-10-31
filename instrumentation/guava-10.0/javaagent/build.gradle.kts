import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("com.google.guava")
    module.set("guava")
    versions.set("[10.0,]")
    skip("32.1.0-android")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("com.google.guava:guava:10.0")
  compileOnly(project(":instrumentation-annotations-support"))

  implementation(project(":instrumentation:guava-10.0:library"))

  testInstrumentation(project(":instrumentation:opentelemetry-extension-annotations-1.0:javaagent"))

  testImplementation(project(":instrumentation-annotations-support-testing"))
  testImplementation(project(":instrumentation-annotations"))
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
}

testing {
  suites {
    register<JvmTestSuite>("version19Test") {
      dependencies {
        implementation("com.google.guava:guava:${baseVersion("19.0").orLatest()}")
      }
    }
  }
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    register("Experimental") {
      jvmArgs("-Dotel.instrumentation.guava.experimental-span-attributes=true")
    }
  }
}

tasks {
  check {
    dependsOn("testExperimental")
    dependsOn(testing.suites.named("version19Test"))
  }
}
