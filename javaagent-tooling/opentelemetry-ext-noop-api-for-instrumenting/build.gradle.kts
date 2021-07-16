plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  implementation("io.opentelemetry:opentelemetry-extension-noop-api")
}

tasks {
  shadowJar {
    dependencies {
      include(dependency("io.opentelemetry:opentelemetry-extension-noop-api"))
    }
    exclude("META-INF/services/io.opentelemetry.context.ContextStorageProvider")
  }

  assemble {
    dependsOn(shadowJar)
  }
}
