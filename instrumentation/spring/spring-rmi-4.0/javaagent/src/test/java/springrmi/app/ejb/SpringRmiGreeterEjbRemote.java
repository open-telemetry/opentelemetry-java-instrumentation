/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app.ejb;

import javax.ejb.Remote;
import springrmi.app.SpringRmiGreeter;

@Remote
public interface SpringRmiGreeterEjbRemote extends SpringRmiGreeter {}
