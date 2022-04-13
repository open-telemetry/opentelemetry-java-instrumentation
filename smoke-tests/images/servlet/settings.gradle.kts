pluginManagement {
  plugins {
    id("com.bmuschko.docker-remote-api") version "7.3.0"
    id("com.diffplug.spotless") version "6.4.2"
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
  }
}

rootProject.name = "matrix"

include(":servlet-3.0")
include(":servlet-5.0")
