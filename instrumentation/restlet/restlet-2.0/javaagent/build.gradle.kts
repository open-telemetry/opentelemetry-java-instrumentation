plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.restlet")
    module.set("org.restlet.jse")
    versions.set("[2.0.0, 2.5-M1]")
    assertInverse.set(true)
  }
}

repositories {
  mavenCentral()
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {
  api(project(":instrumentation:restlet:restlet-2.0:library"))

  library("org.restlet.jse:org.restlet:2.0.2")

  implementation(project(":instrumentation:restlet:restlet-2.0:library"))

  testImplementation(project(":instrumentation:restlet:restlet-2.0:testing"))
  testImplementation("org.restlet.jse:org.restlet.ext.jetty:2.0.2")
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
