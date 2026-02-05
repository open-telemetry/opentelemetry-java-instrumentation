# Smoke Tests

Assert that various applications will start up with the JavaAgent without any obvious ill effects.

Each subproject underneath `smoke-tests/images` produces one or more docker images containing some application
under the test. Various tests in the main module then use them to run the appropriate tests.

The `smoke-tests/extensions` folder contains a test application and packaged instrumentation(s) extension to
test compatibility with existing user-built extensions.
