plugins {
  `java-gradle-plugin`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(gradleApi())
  implementation("io.quarkus:quarkus-gradle-model:2.16.7.Final")
  implementation("io.opentelemetry.instrumentation:quarkus-common-plugin")
}

gradlePlugin {
  plugins {
    create("quarkus20Plugin") {
      id = "io.opentelemetry.instrumentation.quarkus-2.0"
      implementationClass = "io.opentelemetry.instrumentation.quarkus.v2_0.plugin.Quarkus2Plugin"
    }
  }
}
