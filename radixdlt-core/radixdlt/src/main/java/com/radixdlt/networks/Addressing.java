/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.networks;

import com.radixdlt.api.ResourceAddressing;
import com.radixdlt.identifiers.AccountAddressing;
import com.radixdlt.identifiers.ValidatorAddressing;

public final class Addressing {

	public static String accountHrp(int networkId) {
		return Network.ofId(networkId).map(Network::getAccountHrp)
			.orElse("tdx" + networkId);
	}

	public static String validatorHrp(int networkId) {
		return Network.ofId(networkId).map(Network::getValidatorHrp)
			.orElse("vt" + networkId);
	}

	public static String resourceHrpSuffix(int networkId) {
		return Network.ofId(networkId).map(Network::getResourceHrpSuffix)
			.orElse("rt" + networkId);
	}

	private final ValidatorAddressing validatorAddressing;
	private final AccountAddressing accountAddressing;
	private final ResourceAddressing resourceAddressing;

	private Addressing(
		ValidatorAddressing validatorAddressing,
		AccountAddressing accountAddressing,
		ResourceAddressing resourceAddressing
	) {
		this.validatorAddressing = validatorAddressing;
		this.accountAddressing = accountAddressing;
		this.resourceAddressing = resourceAddressing;
	}

	public static Addressing ofNetwork(Network network) {
		return ofNetworkId(network.getId());
	}

	public static Addressing ofNetworkId(int networkId) {
		return new Addressing(
			ValidatorAddressing.bech32(validatorHrp(networkId)),
			AccountAddressing.bech32(accountHrp(networkId)),
			ResourceAddressing.bech32(resourceHrpSuffix(networkId))
		);
	}

	public ValidatorAddressing forValidators() {
		return validatorAddressing;
	}

	public AccountAddressing forAccounts() {
		return accountAddressing;
	}

	public ResourceAddressing forResources() {
		return resourceAddressing;
	}
}
