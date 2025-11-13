plugins {
  id("otel.library-instrumentation")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

dependencies {
  compileOnly("org.springframework:spring-web:3.1.0.RELEASE")

  testLibrary("org.springframework:spring-web:3.1.0.RELEASE")

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

  if (latestDepTest) {
    // Exclude Spring Framework 7.0+ until compatible version available
    testImplementation("org.springframework:spring-web") {
      version {
        strictly("[6.0,7.0[")
      }
    }
  }
}

// spring 6 requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

tasks {
  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }
}
