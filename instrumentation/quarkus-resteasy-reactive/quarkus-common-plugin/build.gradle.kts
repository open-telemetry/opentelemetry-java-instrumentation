plugins {
  `java-library`
}

group = "io.opentelemetry.instrumentation"

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  compileOnly(gradleApi())
  compileOnly("io.quarkus:quarkus-gradle-model:2.16.7.Final")
}
