plugins {
  id("otel.java-conventions")
}

dependencies {
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
  jar {
    manifest {
      attributes("Main-Class" to "io.opentelemetry.smoketest.extensions.app.AppMain")
    }
  }
}
