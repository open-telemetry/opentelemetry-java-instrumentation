import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.xuxueli")
    module.set("xxl-job-core")
    versions.set("[2.1.2,2.3.0)")
    assertInverse.set(true)
  }
}

otelJava {
  // groovy does not support 25-ea
  maxJavaVersionForTests.set(JavaVersion.VERSION_24)
}

dependencies {
  library("com.xuxueli:xxl-job-core:2.1.2") {
    exclude("org.codehaus.groovy", "groovy")
  }
  implementation(project(":instrumentation:xxl-job:xxl-job-common-1.9.2:javaagent"))

  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-1.9.2:javaagent"))
  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-2.3.0:javaagent"))

  testImplementation("org.apache.groovy:groovy")
  testImplementation(project(":instrumentation:xxl-job:xxl-job-common-1.9.2:testing"))
  latestDepTestLibrary("com.xuxueli:xxl-job-core:2.2.+") { // see xxl-job-2.3.0 module
    exclude("org.codehaus.groovy", "groovy")
  }
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    configureEach {
      // required on jdk17
      jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    }
    register("Experimental") {
      telemetryConfig("otel.instrumentation.xxl-job.experimental-span-attributes", "true")
    }
  }
}
