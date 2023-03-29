plugins {
  id("otel.library-instrumentation")
  id("org.graalvm.buildtools.native") version "0.9.14"
}

dependencies {
  implementation("io.opentelemetry:opentelemetry-api-logs")

  // pin the version strictly to avoid overriding by dependencyManagement versions
  compileOnly("ch.qos.logback:logback-classic") {
    version {
      strictly("1.0.0")
    }
  }
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("1.6.4")
    }
  }

  if (findProperty("testLatestDeps") as Boolean) {
    testImplementation("ch.qos.logback:logback-classic:+")
  } else {
    testImplementation("ch.qos.logback:logback-classic") {
      version {
        strictly("1.0.0")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.6.4")
      }
    }
  }

  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

graalvmNative {

  binaries.all {
    resources.autodetect()
  }

  toolchainDetection.set(false)
}

configurations.configureEach {
  exclude("org.apache.groovy", "groovy")
  exclude("org.apache.groovy", "groovy-json")
  exclude("org.spockframework", "spock-core")
}
