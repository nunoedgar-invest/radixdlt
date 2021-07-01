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

package com.radixdlt.application.system.construction;

import com.google.common.collect.Streams;
import com.radixdlt.application.system.scrypt.ValidatorScratchPad;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.NextEpoch;

import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.ExittingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.validators.state.PreparedOwnerUpdate;
import com.radixdlt.application.validators.state.PreparedRakeUpdate;
import com.radixdlt.application.validators.state.PreparedRegisteredUpdate;
import com.radixdlt.application.validators.state.ValidatorData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.application.validators.state.PreparedRakeUpdate.RAKE_MAX;

public final class NextEpochConstructorV3 implements ActionConstructor<NextEpoch> {
	private final UInt256 rewardsPerProposal;
	private final long unstakingEpochDelay;
	private final long minimumCompletedProposalsPercentage;
	private final int maxValidators;

	public NextEpochConstructorV3(
		UInt256 rewardsPerProposal,
		long minimumCompletedProposalsPercentage,
		long unstakingEpochDelay,
		int maxValidators
	) {
		this.rewardsPerProposal = rewardsPerProposal;
		this.unstakingEpochDelay = unstakingEpochDelay;
		this.minimumCompletedProposalsPercentage = minimumCompletedProposalsPercentage;
		this.maxValidators = maxValidators;
	}

	private static ValidatorScratchPad loadValidatorStakeData(
		TxBuilder txBuilder,
		ECPublicKey k,
		TreeMap<ECPublicKey, ValidatorScratchPad> validatorsToUpdate,
		boolean canBeVirtual
	) throws TxBuilderException {
		if (!validatorsToUpdate.containsKey(k)) {
			var validatorData = txBuilder.down(
				ValidatorStakeData.class,
				p -> p.getValidatorKey().equals(k),
				canBeVirtual ? Optional.of(SubstateWithArg.noArg(ValidatorStakeData.createVirtual(k))) : Optional.empty(),
				() -> new TxBuilderException("Validator not found")
			);
			validatorsToUpdate.put(k, new ValidatorScratchPad(validatorData));
		}
		return validatorsToUpdate.get(k);
	}

	private static <T extends ValidatorData, U extends ValidatorData> void prepare(
		TxBuilder txBuilder,
		TreeMap<ECPublicKey, ValidatorScratchPad> validatorsToUpdate,
		Class<T> preparedClass,
		BiConsumer<ValidatorScratchPad, T> updater,
		Function<T, U> copy
	) throws TxBuilderException {
		var preparing = new TreeMap<ECPublicKey, T>(KeyComparator.instance());
		txBuilder.shutdownAll(preparedClass, i -> {
			i.forEachRemaining(update -> preparing.put(update.getValidatorKey(), update));
			return preparing;
		});
		for (var e : preparing.entrySet()) {
			var k = e.getKey();
			var update = e.getValue();
			var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate, true);
			updater.accept(curValidator, update);
			txBuilder.up(copy.apply(update));
		}
	}

	@Override
	public void construct(NextEpoch action, TxBuilder txBuilder) throws TxBuilderException {
		var closedRound = txBuilder.down(
			RoundData.class,
			p -> true,
			Optional.empty(),
			() -> new TxBuilderException("No round data available")
		);
		var closingEpoch = txBuilder.down(
			EpochData.class,
			p -> true,
			Optional.empty(),
			() -> new TxBuilderException("No epoch data available")
		);

		var unlockedStateIndexBuf = ByteBuffer.allocate(2 + Long.BYTES);
		unlockedStateIndexBuf.put(SubstateTypeId.EXITTING_STAKE.id());
		unlockedStateIndexBuf.put((byte) 0);
		unlockedStateIndexBuf.putLong(closingEpoch.getEpoch() + 1);
		var unlockedStakeIndex = SubstateIndex.create(unlockedStateIndexBuf.array(), ExittingStake.class);
		var exitting = txBuilder.shutdownAll(unlockedStakeIndex, (Iterator<ExittingStake> i) -> {
			final TreeSet<ExittingStake> exit = new TreeSet<>(
				(o1, o2) -> Arrays.compare(o1.dataKey(), o2.dataKey())
			);
			i.forEachRemaining(exit::add);
			return exit;
		});
		for (var e : exitting) {
			txBuilder.up(e.unlock());
		}

		var validatorsToUpdate = new TreeMap<ECPublicKey, ValidatorScratchPad>(KeyComparator.instance());
		var validatorBFTData = txBuilder.shutdownAll(ValidatorBFTData.class, i -> {
			final TreeMap<ECPublicKey, ValidatorBFTData> bftData = new TreeMap<>(KeyComparator.instance());
			i.forEachRemaining(e -> bftData.put(e.validatorKey(), e));
			return bftData;
		});
		var preparingStake = new TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>>(KeyComparator.instance());
		for (var e : validatorBFTData.entrySet()) {
			var k = e.getKey();
			var bftData = e.getValue();
			if (bftData.proposalsCompleted() + bftData.proposalsMissed() == 0) {
				continue;
			}
			var percentageCompleted = bftData.proposalsCompleted() * 10000
				/ (bftData.proposalsCompleted() + bftData.proposalsMissed());

			// Didn't pass threshold, no rewards!
			if (percentageCompleted < minimumCompletedProposalsPercentage) {
				continue;
			}

			var nodeRewards = rewardsPerProposal.multiply(UInt256.from(bftData.proposalsCompleted()));
			if (nodeRewards.isZero()) {
				continue;
			}

			var validatorStakeData = loadValidatorStakeData(txBuilder, k, validatorsToUpdate, false);
			int rakePercentage = validatorStakeData.getRakePercentage();
			final UInt256 rakedEmissions;
			if (rakePercentage != 0) {
				var rake = nodeRewards
					.multiply(UInt256.from(rakePercentage))
					.divide(UInt256.from(RAKE_MAX));
				var validatorOwner = validatorStakeData.getOwnerAddr();
				var initStake = new TreeMap<REAddr, UInt256>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()));
				initStake.put(validatorOwner, rake);
				preparingStake.put(k, initStake);
				rakedEmissions = nodeRewards.subtract(rake);
			} else {
				rakedEmissions = nodeRewards;
			}
			validatorStakeData.addEmission(rakedEmissions);
		}

		var allPreparedUnstake = txBuilder.shutdownAll(PreparedUnstakeOwnership.class, i -> {
			var map = new TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>>(KeyComparator.instance());
			i.forEachRemaining(preparedStake ->
				map
					.computeIfAbsent(
						preparedStake.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedStake.getOwner(), preparedStake.getAmount(), UInt256::add)
			);
			return map;
		});
		for (var e : allPreparedUnstake.entrySet()) {
			var k = e.getKey();
			var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate, false);
			var unstakes = e.getValue();
			for (var entry : unstakes.entrySet()) {
				var addr = entry.getKey();
				var amt = entry.getValue();
				var epochUnlocked = closingEpoch.getEpoch() + 1 + unstakingEpochDelay;
				var exittingStake = curValidator.unstakeOwnership(addr, amt, epochUnlocked);
				txBuilder.up(exittingStake);
			}
			validatorsToUpdate.put(k, curValidator);
		}

		var allPreparedStake = txBuilder.shutdownAll(PreparedStake.class, i -> {
			i.forEachRemaining(preparedStake ->
				preparingStake
					.computeIfAbsent(
						preparedStake.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedStake.getOwner(), preparedStake.getAmount(), UInt256::add)
			);
			return preparingStake;
		});
		for (var e : allPreparedStake.entrySet()) {
			var k = e.getKey();
			var stakes = e.getValue();
			var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate, true);
			for (var entry : stakes.entrySet()) {
				var addr = entry.getKey();
				var amt = entry.getValue();

				try {
					var stakeOwnership = curValidator.stake(addr, amt);
					txBuilder.up(stakeOwnership);
				} catch (ProcedureException ex) {
					throw new TxBuilderException(ex);
				}
			}
			validatorsToUpdate.put(k, curValidator);
		}

		var preparingRakeUpdates = new TreeMap<ECPublicKey, PreparedRakeUpdate>(KeyComparator.instance());
		var buf = ByteBuffer.allocate(2 + Long.BYTES);
		buf.put(SubstateTypeId.PREPARED_RAKE_UPDATE.id());
		buf.put((byte) 0);
		buf.putLong(closingEpoch.getEpoch() + 1);
		var index = SubstateIndex.create(buf.array(), PreparedRakeUpdate.class);
		txBuilder.shutdownAll(index, (Iterator<PreparedRakeUpdate> i) -> {
			i.forEachRemaining(preparedValidatorUpdate ->
				preparingRakeUpdates.put(preparedValidatorUpdate.getValidatorKey(), preparedValidatorUpdate)
			);
			return preparingRakeUpdates;
		});
		for (var e : preparingRakeUpdates.entrySet()) {
			var k = e.getKey();
			var update = e.getValue();
			var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate, true);
			curValidator.setRakePercentage(update.getNextRakePercentage());
			txBuilder.up(new ValidatorRakeCopy(k, update.getNextRakePercentage()));
		}

		// Update owners
		prepare(
			txBuilder,
			validatorsToUpdate,
			PreparedOwnerUpdate.class,
			(v, u) -> v.setOwnerAddr(u.getOwnerAddress()),
			u -> new ValidatorOwnerCopy(u.getValidatorKey(), u.getOwnerAddress())
		);

		// Update registered flag
		prepare(
			txBuilder,
			validatorsToUpdate,
			PreparedRegisteredUpdate.class,
			(v, u) -> v.setRegistered(u.isRegistered()),
			u -> new ValidatorRegisteredCopy(u.getValidatorKey(), u.isRegistered())
		);

		validatorsToUpdate.forEach((k, v) -> txBuilder.up(v.toSubstate()));

		try (var cursor = txBuilder.readIndex(
			SubstateIndex.create(new byte[] {SubstateTypeId.VALIDATOR_STAKE_DATA.id(), 0, 1}, ValidatorStakeData.class)
		)) {
			// TODO: Explicitly specify next validatorset
			var nextValidators = Streams.stream(cursor)
				.map(ValidatorStakeData.class::cast)
				.sorted(Comparator.comparing(ValidatorStakeData::getAmount)
					.thenComparing(ValidatorStakeData::getValidatorKey, KeyComparator.instance())
					.reversed()
				)
				.limit(maxValidators)
				.peek(v -> txBuilder.up(new ValidatorBFTData(v.getValidatorKey(), 0, 0)))
				.collect(Collectors.toList());
		}
		txBuilder.up(new EpochData(closingEpoch.getEpoch() + 1));
		txBuilder.up(new RoundData(0, closedRound.getTimestamp()));
		txBuilder.end();
	}
}
