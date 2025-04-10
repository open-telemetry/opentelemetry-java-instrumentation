val stableVersion = "2.15.0"
val alphaVersion = "2.15.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
