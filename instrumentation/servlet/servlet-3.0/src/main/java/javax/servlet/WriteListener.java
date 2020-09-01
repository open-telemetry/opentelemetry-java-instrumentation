/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.servlet;

import java.io.IOException;
import java.util.EventListener;

/**
 * Backport of a callback interface introduced in Servlet 3.1 API. Having it here is required to
 * make {@code Servlet3Instrumentation} work with Servlet 3.0 API, otherwise muzzle would disable it
 * because of a missing class.
 */
public interface WriteListener extends EventListener {

  void onWritePossible() throws IOException;

  void onError(final Throwable t);
}
