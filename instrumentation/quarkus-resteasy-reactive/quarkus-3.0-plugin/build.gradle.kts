plugins {
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(gradleApi())
  implementation("io.quarkus:quarkus-gradle-model:3.0.0.Final")
  implementation("io.opentelemetry.instrumentation:quarkus-common-plugin")
}

gradlePlugin {
  plugins {
    create("quarkus30Plugin") {
      id = "io.opentelemetry.instrumentation.quarkus-3.0"
      implementationClass = "io.opentelemetry.instrumentation.quarkus.v3_0.plugin.Quarkus3Plugin"
    }
  }
}
