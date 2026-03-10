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
}

gradlePlugin {
  plugins {
    create("quarkus2Plugin") {
      id = "io.opentelemetry.instrumentation.quarkus2"
      implementationClass = "io.opentelemetry.instrumentation.quarkus2plugin.Quarkus2Plugin"
    }
  }
}
