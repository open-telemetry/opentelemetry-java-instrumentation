pluginManagement {
  repositories {
    gradlePluginPortal()
    maven {
      name = "sonatype"
      url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
  }
  plugins {
    id("com.gradleup.shadow") version "9.4.1"
    id("io.opentelemetry.instrumentation.muzzle-generation") version "2.28.0-alpha-SNAPSHOT"
    id("io.opentelemetry.instrumentation.muzzle-check") version "2.28.0-alpha-SNAPSHOT"
  }
}

rootProject.name = "opentelemetry-java-instrumentation-distro-demo"

include("agent")
include("bootstrap")
include("custom")
include("instrumentation")
include("instrumentation:servlet-3")
include("smoke-tests")
include("testing:agent-for-testing")
