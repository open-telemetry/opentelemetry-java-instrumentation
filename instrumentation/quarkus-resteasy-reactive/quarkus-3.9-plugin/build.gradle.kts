plugins {
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(gradleApi())
  implementation("io.quarkus:quarkus-gradle-model:3.9.5")
}

gradlePlugin {
  plugins {
    create("quarkus39Plugin") {
      id = "io.opentelemetry.instrumentation.quarkus-3.9"
      implementationClass = "io.opentelemetry.instrumentation.quarkus.v3_9.plugin.Quarkus39Plugin"
    }
  }
}
