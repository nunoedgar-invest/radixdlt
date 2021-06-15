/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.api.data.action;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.stream.Stream;

class BurnAction implements TransactionAction {
	private final REAddr from;
	private final UInt256 amount;
	private final REAddr rri;

	BurnAction(REAddr from, UInt256 amount, REAddr rri) {
		this.from = from;
		this.amount = amount;
		this.rri = rri;
	}

	@Override
	public REAddr getFrom() {
		return from;
	}

	@Override
	public Stream<TxAction> toAction() {
		return Stream.of(new BurnToken(TransactionAction.rriValue(rri), from, amount));
	}
}
