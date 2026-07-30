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

testing {
  suites {
    register<JvmTestSuite>("vaadin142Test") {
      dependencies {
        implementation(project(":instrumentation:vaadin-14.2:testing"))
        implementation("com.vaadin:vaadin-spring-boot-starter:14.2.0")
      }
    }

    register<JvmTestSuite>("vaadin16Test") {
      dependencies {
        implementation(project(":instrumentation:vaadin-14.2:testing"))
        implementation("com.vaadin:vaadin-spring-boot-starter:16.0.0")
      }
    }

    register<JvmTestSuite>("vaadin14LatestTest") {
      dependencies {
        implementation(project(":instrumentation:vaadin-14.2:testing"))
        // 14.12 requires license
        implementation("com.vaadin:vaadin-spring-boot-starter:14.11.+")
      }
    }

    register<JvmTestSuite>("vaadinLatestTest") {
      dependencies {
        implementation(project(":instrumentation:vaadin-14.2:testing"))
        // tests fail with 24.4.1
        implementation("com.vaadin:vaadin-spring-boot-starter:24.3.13")
      }
    }
  }
}

// Vaadin's frontend tooling installs node and pnpm into the shared ~/.vaadin directory. Running two
// test suites at once lets their npm installs clobber each other, which leaves ~/.vaadin corrupted
// and fails every subsequent attempt with ENOTEMPTY, so only let one suite run at a time.
abstract class VaadinBuildService : BuildService<BuildServiceParameters.None>

val vaadinBuildService =
  gradle.sharedServices.registerIfAbsent("vaadinBuildService", VaadinBuildService::class.java) {
    maxParallelUsages.set(1)
  }

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    usesService(vaadinBuildService)

    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
    systemProperty("collectMetadata", otelProps.collectMetadata)
    // Enable legacy OpenSSL provider for Node.js 17+ compatibility with webpack 4
    environment("NODE_OPTIONS", "--openssl-legacy-provider")
  }

  check {
    if (otelProps.testLatestDeps) {
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
