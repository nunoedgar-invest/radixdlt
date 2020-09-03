/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * Ledger state
 */
@Immutable
@SerializerId2("consensus.ledger_state")
public final class LedgerState implements Comparable<LedgerState> {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("stateVersion")
	@DsonOutput(Output.ALL)
	private final long stateVersion;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	@JsonProperty("command_id")
	@DsonOutput(Output.ALL)
	private final Hash commandId; // TODO: Change to accumulator

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private final long timestamp; // TODO: Move into command accumulator

	@JsonProperty("isEndOfEpoch")
	@DsonOutput(Output.ALL)
	private final boolean isEndOfEpoch;

	// TODO: Replace isEndOfEpoch with nextValidatorSet
	@JsonCreator
	private LedgerState(
		@JsonProperty("epoch") long epoch,
		@JsonProperty("stateVersion") long stateVersion,
		@JsonProperty("command_id") Hash commandId,
		@JsonProperty("timestamp") long timestamp,
		@JsonProperty("isEndOfEpoch") boolean isEndOfEpoch
	) {
		this.epoch = epoch;
		this.stateVersion = stateVersion;
		this.commandId = commandId;
		this.isEndOfEpoch = isEndOfEpoch;
		this.timestamp = timestamp;
	}

	public static LedgerState create(
		long epoch,
		long stateVersion,
		Hash commandId,
		long timestamp,
		boolean isEndOfEpoch
	) {
		return new LedgerState(epoch, stateVersion, commandId, timestamp, isEndOfEpoch);
	}

	public Hash getCommandId() {
		return commandId;
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public boolean isEndOfEpoch() {
		return isEndOfEpoch;
	}

	public long timestamp() {
		return this.timestamp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.stateVersion, this.commandId, this.timestamp, this.epoch, this.isEndOfEpoch);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof LedgerState) {
			LedgerState other = (LedgerState) o;
			return this.timestamp == other.timestamp
				&& this.stateVersion == other.stateVersion
				&& Objects.equals(this.commandId, other.commandId)
				&& this.epoch == other.epoch
				&& this.isEndOfEpoch == other.isEndOfEpoch;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{stateVersion=%s timestamp=%s epoch=%s, isEndOfEpoch=%s}",
			getClass().getSimpleName(), this.stateVersion, this.timestamp, this.epoch, this.isEndOfEpoch
		);
	}

	@Override
	public int compareTo(LedgerState o) {
		if (o.epoch != this.epoch) {
			return this.epoch > o.epoch ? 1 : -1;
		}

		if (o.stateVersion != this.stateVersion) {
			return this.stateVersion > o.stateVersion ? 1 : -1;
		}

		if (o.isEndOfEpoch != this.isEndOfEpoch) {
			return this.isEndOfEpoch ? 1 : -1;
		}

		return 0;
	}
}
