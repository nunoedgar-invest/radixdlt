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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.epoch.LocalViewUpdate;
import com.radixdlt.consensus.epoch.LocalViewUpdateSender;
import com.radixdlt.consensus.liveness.PacemakerState.ViewUpdateSender;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.sync.BFTSyncResponseProcessor;
import com.radixdlt.consensus.liveness.PacemakerInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.epoch.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.integration.distributed.BFTRunner;

public class MockedConsensusRunnerModule extends AbstractModule {
	@Override
	public void configure() {
		MapBinder<String, ModuleRunner> moduleRunners = MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class);
		moduleRunners.addBinding("consensus").to(BFTRunner.class).in(Scopes.SINGLETON);
		bind(BFTSyncResponseProcessor.class).to(BFTSync.class).in(Scopes.SINGLETON);
	}

	@Provides
	public PacemakerInfoSender pacemakerInfoSender(
		EventProcessor<Timeout> timeoutEventProcessor,
		EventProcessor<EpochView> epochViewEventProcessor,
		ProposerElection proposerElection
	) {
		return new PacemakerInfoSender() {
			@Override
			public void sendCurrentView(View view) {
				epochViewEventProcessor.processEvent(EpochView.of(1, view));
			}

			@Override
			public void sendTimeoutProcessed(View view) {
				BFTNode leader = proposerElection.getProposer(view);
				timeoutEventProcessor.processEvent(new Timeout(EpochView.of(1, view), leader));
			}
		};
	}

	@Provides
	private PacemakerTimeoutSender initialTimeoutSender(LocalTimeoutSender localTimeoutSender) {
		 return (view, ms) -> localTimeoutSender.scheduleTimeout(new LocalTimeout(1, view), ms);
	}

	@Provides
	private ViewUpdateSender initialViewUpdateSender(LocalViewUpdateSender localViewUpdateSender) {
		return (view) -> localViewUpdateSender.sendLocalViewUpdate(new LocalViewUpdate(1, view));
	}
}
