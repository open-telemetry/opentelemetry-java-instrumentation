plugins {
  id("otel.javaagent-instrumentation")
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
    versions.set("[3.1.0,)")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("com.vaadin:flow-server:2.2.0")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-7.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:tomcat:tomcat-10.0:javaagent"))
}

testing {
  suites {
    val vaadin142Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vaadin-14.2:testing"))
        implementation("com.vaadin:vaadin-spring-boot-starter:14.2.0")
      }
    }

    val vaadin16Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vaadin-14.2:testing"))
        implementation("com.vaadin:vaadin-spring-boot-starter:16.0.0")
      }
    }

    val vaadin14LatestTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vaadin-14.2:testing"))
        // 14.12 requires license
        implementation("com.vaadin:vaadin-spring-boot-starter:14.11.+")
      }
    }

    val vaadinLatestTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:vaadin-14.2:testing"))
        // tests fail with 24.4.1
        implementation("com.vaadin:vaadin-spring-boot-starter:24.3.13")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  check {
    if (findProperty("testLatestDeps") as Boolean) {
      dependsOn(testing.suites.named("vaadin14LatestTest"), testing.suites.named("vaadinLatestTest"))
    } else {
      dependsOn(testing.suites.named("vaadin142Test"), testing.suites.named("vaadin16Test"))
    }
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
tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  jvmArgs("-Dotel.instrumentation.common.experimental.view-telemetry.enabled=true")
}
