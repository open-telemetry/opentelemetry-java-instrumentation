val stableVersion = "2.17.0-SNAPSHOT"
val alphaVersion = "2.17.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
