plugins {
  id("otel.library-instrumentation")
}

repositories {
  mavenCentral()
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {

  library("org.restlet.jse:org.restlet:2.0.2")

  testImplementation(project(":instrumentation:restlet:restlet-2.0:testing"))
  testLibrary("org.restlet.jse:org.restlet.ext.jetty:2.0.2")
}
// restlet registers the first engine that is present on classpath, so we need to enforce the appropriate version
if (findProperty("testLatestDeps") as Boolean) {
  configurations.configureEach {
    resolutionStrategy {
      eachDependency {
        if (requested.group == "org.restlet.jse") {
          useVersion("2.+")
        }
      }
    }
  }
}
