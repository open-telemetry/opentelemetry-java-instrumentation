plugins {
  id("otel.library-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:runtime-telemetry:library"))
  testImplementation("io.github.netmikey.logunit:logunit-jul:1.1.3")
}

tasks {
  compileJava {
    // We compile this module for java 8 because it is used as a dependency in spring-boot-autoconfigure.
    // If this module is compiled for java 17 then gradle can figure out based on the metadata that
    // spring-boot-autoconfigure has a dependency that requires 17 and fails the build when it is used
    // in a project that targets an earlier java version.
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13384
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.release.set(null as Int?)
  }
}
