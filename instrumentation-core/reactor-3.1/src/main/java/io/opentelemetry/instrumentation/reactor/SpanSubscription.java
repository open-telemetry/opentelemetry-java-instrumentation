/*
 * Copyright The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentelemetry.instrumentation.reactor;

import org.reactivestreams.Subscription;

import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;

/**
 * A {@link SpanSubscription} is a {@link Subscription} that fakes being {@link Fuseable}
 * (implementing {@link reactor.core.Fuseable.QueueSubscription} with default no-op methods
 * and always negotiating fusion to be {@link Fuseable#NONE}).
 *
 * @author Marcin Grzejszczak
 */
public interface SpanSubscription<T> extends CoreSubscriber<T>, Fuseable.QueueSubscription<T> {

	@Override
	default T poll() {
		return null;
	}

	@Override
	default int requestFusion(int i) {
		return Fuseable.NONE; //always negotiate to no fusion
	}

	@Override
	default int size() {
		return 0;
	}

	@Override
	default boolean isEmpty() {
		return true;
	}

	@Override
	default void clear() {
		//NO-OP
	}

}