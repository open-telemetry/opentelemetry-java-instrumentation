plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.restlet.jse")
    module.set("org.restlet")
    versions.set("[2.0.0,)")
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
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.restlet.jse:org.restlet:2.0.2")

  implementation(project(":instrumentation:restlet:restlet-2.0:library"))

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

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
