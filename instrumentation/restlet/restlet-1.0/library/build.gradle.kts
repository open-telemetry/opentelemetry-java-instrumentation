plugins {
  id("otel.library-instrumentation")
}

repositories {
  mavenCentral()
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  library("org.restlet:org.restlet:1.1.5")
  library("com.noelios.restlet:com.noelios.restlet:1.1.5")

  testImplementation(project(":instrumentation:restlet:restlet-1.0:testing"))
  testImplementation("org.restlet:org.restlet.ext.spring:1.1.5")
  testImplementation("org.springframework:spring:2.5.6")

  latestDepTestLibrary("org.restlet:org.restlet:1.1.+")
  latestDepTestLibrary("com.noelios.restlet:com.noelios.restlet:1.1.+")
}
