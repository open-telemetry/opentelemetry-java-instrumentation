plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.slf4j")
    module.set("slf4j-api")
    versions.set("[1.4.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.springframework.boot")
    module.set("spring-boot")
    // pre-1.2.0 versions don't have the Slf4JLoggingSystem
    versions.set("[1.2.0,)")
    extraDependency("org.springframework.boot:spring-boot-starter-logging")
  }
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
dependencies {
  bootstrap(project(":instrumentation:internal:internal-application-logger:bootstrap"))

  compileOnly(project(":javaagent-bootstrap"))

  compileOnly("org.slf4j:slf4j-api") {
    version {
      // 1.4.0 introduced the TRACE logging level
      strictly("1.4.0")
    }
  }

  if (latestDepTest) {
    testImplementation("ch.qos.logback:logback-classic:latest.release")
  } else {
    testImplementation("ch.qos.logback:logback-classic") {
      version {
        strictly("1.2.11")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.7.36")
      }
    }
  }

  testLibrary("org.springframework.boot:spring-boot-starter:2.5.3")
}

if (latestDepTest) {
  // spring 6 requires java 17
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}
