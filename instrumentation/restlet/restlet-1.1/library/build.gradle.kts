plugins {
  id("otel.library-instrumentation")
}

repositories {
  mavenCentral()
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {

  library("org.restlet:org.restlet:1.1.5")
  library("com.noelios.restlet:com.noelios.restlet:1.1.5")

  testImplementation(project(":instrumentation:restlet:restlet-1.1:testing"))

  latestDepTestLibrary("org.restlet:org.restlet:1.+") // see restlet-2.0 module
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
