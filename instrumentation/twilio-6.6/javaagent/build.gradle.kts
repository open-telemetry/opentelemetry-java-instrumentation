import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.twilio.sdk")
    module.set("twilio")
    versions.set("(,8.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  // this is first version in maven central (there's a 0.0.1 but that is really 7.14.4)
  library("com.twilio.sdk:twilio:6.6.9")

  // included to make sure the apache httpclient nested spans are suppressed
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))

  latestDepTestLibrary("com.twilio.sdk:twilio:7.+") // documented limitation
}

tasks {
  withType<Test>().configureEach {

    if (name != "testDisabled") {
      jvmArgs("-Dotel.instrumentation.twilio.enabled=true")
    }
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("Experimental") {
          jvmArgs("-Dotel.instrumentation.twilio.experimental-span-attributes=true")
          systemProperty("metadataConfig", "otel.instrumentation.twilio.experimental-span-attributes=true")
        }
        register("Disabled") {
          filter {
            includeTestsMatching("*DefaultEnablementTest")
          }
        }
      }
    }
  }
}
