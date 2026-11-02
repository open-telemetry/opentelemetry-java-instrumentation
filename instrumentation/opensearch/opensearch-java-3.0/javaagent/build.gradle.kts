import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.opensearch.client")
    module.set("opensearch-java")
    versions.set("[3.0,)")
    assertInverse.set(true)
    extraDependency("org.opensearch.client:opensearch-rest-client:3.0.0")
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  library("org.opensearch.client:opensearch-java:3.0.0")

  implementation(project(":instrumentation:opensearch:opensearch-rest-common-1.0:javaagent"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  compileOnly("org.opensearch.client:opensearch-rest-client:3.0.0")
  annotationProcessor("com.google.auto.value:auto-value")
  compileOnly("com.fasterxml.jackson.core:jackson-core")

  testImplementation("org.opensearch.client:opensearch-rest-client:3.0.0")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation(project(":instrumentation:opensearch:opensearch-rest-common-1.0:testing"))
  testInstrumentation(project(":instrumentation:opensearch:opensearch-rest-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:javaagent"))

  // AwsSdk2Transport supports awssdk version 2.26.0
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testImplementation("software.amazon.awssdk:auth:2.26.0")
  testImplementation("software.amazon.awssdk:identity-spi:2.26.0")
  testImplementation("software.amazon.awssdk:apache-client:2.26.0")
  testImplementation("software.amazon.awssdk:netty-nio-client:2.26.0")
  testImplementation("software.amazon.awssdk:regions:2.26.0")
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    configureEach {
      usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    }
    configureDefault {
      filter {
        excludeTestsMatching("OpenSearchQuerySanitizationDisabledTest")
        excludeTestsMatching("OpenSearchQuerySanitizationDisabledJsonbTest")
      }
    }
    register("QuerySanitizationDisabled") {
      filter {
        includeTestsMatching("OpenSearchQuerySanitizationDisabledTest")
        includeTestsMatching("OpenSearchQuerySanitizationDisabledJsonbTest")
      }
      telemetryConfig("otel.instrumentation.opensearch.query-sanitization.enabled", "false")
    }
  }
}
