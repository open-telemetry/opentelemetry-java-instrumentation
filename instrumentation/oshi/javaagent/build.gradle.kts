plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.github.oshi")
    module.set("oshi-core")
    versions.set("[5.3.1,)")
    // Could not parse POM https://repo.maven.apache.org/maven2/com/github/oshi/oshi-core/6.1.1/oshi-core-6.1.1.pom
    skip("6.1.1")
  }
}

dependencies {
  implementation(project(":instrumentation:oshi:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi:testing"))

  // Could not parse POM https://repo.maven.apache.org/maven2/com/github/oshi/oshi-core/6.1.1/oshi-core-6.1.1.pom
  latestDepTestLibrary("com.github.oshi:oshi-core:6.1.0")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.oshi.experimental-metrics.enabled=true")
  }
}
