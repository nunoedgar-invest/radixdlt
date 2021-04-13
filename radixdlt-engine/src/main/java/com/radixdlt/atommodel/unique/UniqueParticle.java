/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel.unique;

import com.radixdlt.identifiers.RRI;
import com.radixdlt.constraintmachine.Particle;
import java.util.Objects;

public final class UniqueParticle extends Particle {
	private final RRI rri;

	public UniqueParticle(RRI rri) {
		this.rri = Objects.requireNonNull(rri);
	}

	public RRI getRri() {
		return rri;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.rri);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UniqueParticle)) {
			return false;
		}
		final var that = (UniqueParticle) obj;
		return Objects.equals(this.rri, that.rri);
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), rri);
	}
}
