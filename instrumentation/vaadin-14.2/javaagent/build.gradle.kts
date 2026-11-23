import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
}

muzzle {
  fail {
    group.set("com.vaadin")
    module.set("flow-server")
    versions.set("[,2.2.0)")
  }
  pass {
    group.set("com.vaadin")
    module.set("flow-server")
    versions.set("[2.2.0,3)")
  }
  fail {
    group.set("com.vaadin")
    module.set("flow-server")
    versions.set("[3.0.0,3.1.0)")
  }
  pass {
    group.set("com.vaadin")
    module.set("flow-server")
    versions.set("[3.1.0,25.0.0)")
  }
  // not supported yet
  fail {
    group.set("com.vaadin")
    module.set("flow-server")
    versions.set("[25.0.0,)")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("com.vaadin:flow-server:2.2.0")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))
}

testing.suites {
  register<JvmTestSuite>("vaadin14Test") {
    dependencies {
      implementation(project(":instrumentation:vaadin-14.2:testing"))
      // 14.12 requires license
      implementation(
        "com.vaadin:vaadin-spring-boot-starter:${baseVersion("14.2.0").orLatest("14.11.+")}"
      )
    }
  }

  register<JvmTestSuite>("vaadinLatestTest") {
    dependencies {
      implementation(project(":instrumentation:vaadin-14.2:testing"))
      // tests fail with 24.4.1
      implementation("com.vaadin:vaadin-spring-boot-starter:24.3.13")
      implementation("org.testcontainers:testcontainers-selenium")
      implementation("org.seleniumhq.selenium:selenium-java:4.8.3")
    }
  }

  executions("vaadin14Test") {
    register("ControllerTelemetry") {
      telemetryConfig("otel.instrumentation.common.controller-telemetry.enabled", "true")
    }
  }
  if (otelProps.testLatestDeps) {
    executions("vaadinLatestTest") {
      register("ControllerTelemetry") {
        telemetryConfig("otel.instrumentation.common.controller-telemetry.enabled", "true")
      }
    }
  }
}

tasks.check {
  dependsOn(testing.suites.named("vaadin14Test"))
  if (otelProps.testLatestDeps) {
    dependsOn(testing.suites.named("vaadinLatestTest"))
  }
}

configurations.configureEach {
  if (!this.name.startsWith("vaadinLatestTest")) {
    resolutionStrategy {
      // requires old logback (and therefore also old slf4j)
      force("ch.qos.logback:logback-classic:1.2.11")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
