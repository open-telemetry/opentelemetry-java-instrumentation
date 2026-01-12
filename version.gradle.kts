val stableVersion = "2.24.0-SNAPSHOT"
val alphaVersion = "2.24.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
