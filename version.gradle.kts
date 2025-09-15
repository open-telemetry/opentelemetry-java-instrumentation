val stableVersion = "2.21.0-SNAPSHOT"
val alphaVersion = "2.21.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
