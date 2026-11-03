import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("io.reactivex.rxjava3")
    module.set("rxjava")
    versions.set("[3.1.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.1.1")
  compileOnly(project(":instrumentation-annotations-support"))

  implementation(project(":instrumentation:rxjava:rxjava-3.1.1:library"))

  testImplementation(project(":instrumentation:rxjava:rxjava-common-3.0:testing"))

  testInstrumentation(project(":instrumentation:opentelemetry-extension-annotations-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-3.0:javaagent"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("Experimental") {
          jvmArgs("-Dotel.instrumentation.rxjava.experimental-span-attributes=true")
        }
      }
    }
  }
}
