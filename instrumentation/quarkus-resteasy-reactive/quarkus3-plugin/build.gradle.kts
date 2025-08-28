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
}

gradlePlugin {
  plugins {
    create("quarkus3Plugin") {
      id = "io.opentelemetry.instrumentation.quarkus3"
      implementationClass = "io.opentelemetry.instrumentation.quarkus3plugin.Quarkus3Plugin"
    }
  }
}
