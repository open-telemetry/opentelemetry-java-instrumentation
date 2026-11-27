import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.cxf")
    module.set("cxf-rt-frontend-jaxrs")
    versions.set("[4.0,5)")
    assertInverse.set(true)
    extraDependency("jakarta.servlet:jakarta.servlet-api:5.0.0")
  }
}

otelJava {
  minJavaVersionSupported.set(if (otelProps.testLatestDeps) JavaVersion.VERSION_17 else JavaVersion.VERSION_11)
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
  library("org.apache.cxf:cxf-rt-frontend-jaxrs:4.0.0")

  implementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:javaagent"))

  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-cxf-3.2:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-11.0:javaagent"))
  testInstrumentation(project(":instrumentation:jetty:jetty-12.0:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))
  testImplementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:testing"))
  testImplementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.50")
  testImplementation("javax.servlet:javax.servlet-api:3.1.0")
  testImplementation("javax.ws.rs:javax.ws.rs-api:2.0")
  testLibrary("org.apache.cxf:cxf-rt-transports-http:4.0.0")
  testLibrary("org.apache.cxf:cxf-rt-transports-http-jetty:4.0.0")
  latestDepTestLibrary("org.apache.cxf:cxf-rt-frontend-jaxrs:4.+") // documented limitation
  latestDepTestLibrary("org.apache.cxf:cxf-rt-transports-http:4.+") // documented limitation
  latestDepTestLibrary("org.apache.cxf:cxf-rt-transports-http-jetty:4.+") // documented limitation
}

testing.suites {
  executions("test") {
    register("ControllerTelemetry") {
      filter { includeTestsMatching("*CxfFilterTest") }
      telemetryConfig("otel.instrumentation.common.controller-telemetry.enabled", "true")
    }
  }
}
