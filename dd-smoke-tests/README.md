# Datadog Smoke Tests
Assert that various applications will start up with the Datadog JavaAgent without any obvious ill effects.

Each subproject underneath `dd-smoke-tests` is a single smoke test. Each test does the following
* Launch the application with stdout and stderr logged to `$buildDir/reports/server.log`
* For web servers, run a spock test which does 200 requests to an endpoint on the server and asserts on an expected response.

Note that there is nothing special about doing 200 requests. 200 is simply an arbitrarily large number to exercise the server.
