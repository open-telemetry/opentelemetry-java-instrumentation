plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.github.oshi")
    module.set("oshi-core")
    versions.set("[5.3.1,)")
  }
}

dependencies {
  implementation(project(":instrumentation:oshi:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi:testing"))
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.oshi.experimental-metrics.enabled=true")
  }
}
