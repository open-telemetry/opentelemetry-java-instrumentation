/*
 * Copyright 2019 Datadog
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
package com.datadog.profiling.controller;

/**
 * Interface for the low lever flight recorder control functionality. Needed since we will likely
 * want to support multiple version later.
 */
public interface Controller {
  /**
   * Creates a continuous recording using the specified template.
   *
   * @param recordingName the name under which the recording will be known.
   * @return the recording object created.
   */
  OngoingRecording createRecording(String recordingName);
}
