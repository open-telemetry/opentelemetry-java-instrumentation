import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-testing")
}

dependencies {
  // Servlet instrumentation is tested in Tomcat without Tomcat instrumentation, which is disabled
  // by default and only enabled in the TomcatInstrumentation execution.
  // In Jetty, it is tested with the Jetty app server instrumentation.
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-5.0:testing"))

  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:10.0.0")
}

if (otelProps.testLatestDeps) {
  otelJava {
    // Tomcat 10.1 requires Java 11
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}

testing.suites {
  executions("test") {
    configureEach {
      // exercises an exclude-only selector, which captures every parameter that it does not exclude
      jvmArgs("-Dotel.instrumentation.servlet.experimental.request-parameters.excluded=ignored-*")
    }

    configureDefault {
      // ensures tomcat instrumentation remains disabled for servlet-only instrumentation
      jvmArgs("-Dotel.instrumentation.tomcat-10.0.enabled=false")
    }

    register("Experimental") {
      // ensures tomcat instrumentation remains disabled for servlet-only instrumentation
      jvmArgs("-Dotel.instrumentation.tomcat-10.0.enabled=false")

      jvmArgs("-Dotel.instrumentation.servlet.experimental.trace-id-request-attribute.enabled=true")
      jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-body.enabled=true")
    }

    // test body capture with tomcat + servlet instrumentation enabled
    // as body capture behavior differs when tomcat creates the request span
    register("TomcatInstrumentation") {
      filter {
        includeTestsMatching("*requestBodyCapture*")
      }
      jvmArgs("-Dotel.instrumentation.servlet.experimental.capture-request-body.enabled=true")
    }
  }
}
