pluginManagement {
  repositories {
    gradlePluginPortal()
    maven {
      name = "sonatype"
      url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
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
include("dependencyManagement") 