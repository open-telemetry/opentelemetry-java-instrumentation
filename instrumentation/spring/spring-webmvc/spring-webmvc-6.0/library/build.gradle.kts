plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("org.springframework:spring-webmvc:6.0.0")
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testImplementation(project(":testing-common"))
  testImplementation("org.springframework.boot:spring-boot-starter-web:3.0.0")
  testImplementation("org.springframework.boot:spring-boot-starter-test:3.0.0")
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  compileJava {
    // We compile this module for java 8 because it is used as a dependency in spring-boot-autoconfigure.
    // If this module is compiled for java 17 then gradle can figure out based on the metadata that
    // spring-boot-autoconfigure has a dependency that requires 17 and fails the build when it is used
    // in a project that targets an earlier java version.
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/9949
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.release.set(null as Int?)
  }
}
