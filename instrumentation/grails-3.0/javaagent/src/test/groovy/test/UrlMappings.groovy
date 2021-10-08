/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

class UrlMappings {

  static mappings = {
    "/success"(controller: 'test', action: 'success')
    "/query"(controller: 'test', action: 'query')
    "/redirect"(controller: 'test', action: 'redirect')
    "/error-status"(controller: 'test', action: 'error')
    "/exception"(controller: 'test', action: 'exception')
    "/path/$id/param"(controller: 'test', action: 'path')
    "/captureHeaders"(controller: 'test', action: 'captureHeaders')

    "500"(controller: 'error')
    "404"(controller: 'error', action: 'notFound')
  }
}
