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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.VertexStoreEventProcessor;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;

import com.google.common.collect.ImmutableList;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.Logger;

/**
 * Manages the BFT Vertex chain.
 * TODO: Move this logic into ledger package.
 */
@NotThreadSafe
public final class VertexStore implements VertexStoreEventProcessor {
	public interface GetVerticesRequest {
		Hash getVertexId();
		int getCount();
	}

	public interface SyncedVertexSender {
		void sendSyncedVertex(VerifiedVertex vertex);
	}

	public interface VertexStoreEventSender {
		void sendCommittedVertex(VerifiedVertex vertex);
		void highQC(QuorumCertificate qc);
	}

	/**
	 * An asynchronous supplier which retrieves data for a vertex with a given id
	 */
	public interface SyncVerticesRPCSender {
		/**
		 * Send an RPC request to retrieve vertices given an Id and number of
		 * vertices. i.e. The vertex with the given id and (count - 1) ancestors
		 * will be returned.
		 *
		 * @param id the id of the vertex to retrieve
		 * @param node the node to retrieve the vertex info from
		 * @param count number of vertices to retrieve
		 * @param opaque an object which is expected to be provided in the corresponding response
		 */
		void sendGetVerticesRequest(Hash id, BFTNode node, int count, Object opaque);

		/**
		 * Send an RPC response to a given request
		 * @param originalRequest the original request which is being replied to
		 * @param vertices the response data of vertices
		 */
		void sendGetVerticesResponse(GetVerticesRequest originalRequest, ImmutableList<VerifiedVertex> vertices);

		/**
		 * Send an RPC error response to a given request
		 * @param originalRequest the original request
		 * @param highestQC highestQC sync info
		 * @param highestCommittedQC highestCommittedQC sync info
		 */
		void sendGetVerticesErrorResponse(GetVerticesRequest originalRequest, QuorumCertificate highestQC, QuorumCertificate highestCommittedQC);
	}

	private final Logger log;
	private final VertexStoreEventSender vertexStoreEventSender;
	private final SyncedVertexSender syncedVertexSender;
	private final SyncVerticesRPCSender syncVerticesRPCSender;
	private final Ledger ledger;
	private final SystemCounters counters;
	private final SyncRequestSender syncRequestSender;

	// These should never be null
	private Hash rootId;
	private QuorumCertificate highestQC;
	private QuorumCertificate highestCommittedQC;

	private final Map<Hash, VerifiedVertex> vertices = new HashMap<>();
	private final Map<Hash, SyncState> syncing = new HashMap<>();
	private final Map<LedgerHeader, List<Hash>> committedSyncing = new HashMap<>();

	public VertexStore(
		VerifiedVertex rootVertex,
		QuorumCertificate rootQC,
		Ledger ledger,
		SyncVerticesRPCSender syncVerticesRPCSender,
		SyncedVertexSender syncedVertexSender,
		VertexStoreEventSender vertexStoreEventSender,
		SyncRequestSender syncRequestSender,
		SystemCounters counters,
		Logger log
	) {
		this(
			rootVertex,
			rootQC,
			Collections.emptyList(),
			ledger,
			syncVerticesRPCSender,
			syncedVertexSender,
			vertexStoreEventSender,
			syncRequestSender,
			counters,
			log
		);
	}

	public VertexStore(
		VerifiedVertex rootVertex,
		QuorumCertificate rootQC,
		List<VerifiedVertex> vertices,
		Ledger ledger,
		SyncVerticesRPCSender syncVerticesRPCSender,
		SyncedVertexSender syncedVertexSender,
		VertexStoreEventSender vertexStoreEventSender,
		SyncRequestSender syncRequestSender,
		SystemCounters counters,
		Logger log
	) {
		this.ledger = Objects.requireNonNull(ledger);
		this.syncVerticesRPCSender = Objects.requireNonNull(syncVerticesRPCSender);
		this.vertexStoreEventSender = Objects.requireNonNull(vertexStoreEventSender);
		this.syncedVertexSender = Objects.requireNonNull(syncedVertexSender);
		this.syncRequestSender = Objects.requireNonNull(syncRequestSender);
		this.counters = Objects.requireNonNull(counters);
		this.log = Objects.requireNonNull(log);

		Objects.requireNonNull(rootVertex);
		Objects.requireNonNull(rootQC);
		Objects.requireNonNull(vertices);

		this.rebuild(rootVertex, rootQC, rootQC, vertices);
	}

	private VerifiedVertex getRoot() {
		return this.vertices.get(this.rootId);
	}

	private void rebuild(VerifiedVertex rootVertex, QuorumCertificate rootQC, QuorumCertificate rootCommitQC, List<VerifiedVertex> vertices) {
		if (!rootQC.getProposed().getVertexId().equals(rootVertex.getId())) {
			throw new IllegalStateException(String.format("rootQC=%s does not match rootVertex=%s", rootQC, rootVertex));
		}

		final Optional<BFTHeader> header = rootCommitQC.getCommittedAndLedgerStateProof().map(Pair::getFirst);
		if (!header.isPresent()) {
			if (!rootQC.getView().isGenesis() || !rootQC.equals(rootCommitQC)) {
				throw new IllegalStateException(String.format("rootCommit=%s does not have commit", rootCommitQC));
			}
		} else if (!header.get().getVertexId().equals(rootVertex.getId())) {
			throw new IllegalStateException(String.format("rootCommitQC=%s does not match rootVertex=%s", rootCommitQC, rootVertex));
		}

		this.vertices.clear();
		this.rootId = rootVertex.getId();
		this.highestQC = rootQC;
		this.vertexStoreEventSender.highQC(rootQC);
		this.highestCommittedQC = rootCommitQC;
		this.vertices.put(rootVertex.getId(), rootVertex);

		for (VerifiedVertex vertex : vertices) {
			if (!addQC(vertex.getQC())) {
				throw new IllegalStateException(String.format("Missing qc=%s", vertex.getQC()));
			}

			try {
				insertVertexInternal(vertex);
			} catch (VertexInsertionException e) {
				throw new IllegalStateException("Could not insert vertex " + vertex, e);
			}
		}
	}

	private enum SyncStage {
		PREPARING,
		GET_COMMITTED_VERTICES,
		SYNC_TO_COMMIT,
		GET_QC_VERTICES
	}

	private static class SyncState {
		private final Hash localSyncId;
		private final QuorumCertificate qc;
		private final QuorumCertificate committedQC;
		private final BFTHeader committedHeader;
		private final VerifiedLedgerHeaderAndProof committedProof;
		private final BFTNode author;
		private SyncStage syncStage;
		private final LinkedList<VerifiedVertex> fetched = new LinkedList<>();

		SyncState(Hash localSyncId, QuorumCertificate qc, QuorumCertificate committedQC, BFTNode author) {
			this.localSyncId = localSyncId;
			Pair<BFTHeader, VerifiedLedgerHeaderAndProof> pair = committedQC.getCommittedAndLedgerStateProof()
				.orElseThrow(() -> new IllegalStateException("committedQC must have a commit"));
			this.committedHeader = pair.getFirst();
			this.committedProof = pair.getSecond();
			this.qc = qc;
			this.committedQC = committedQC;
			this.author = author;
			this.syncStage = SyncStage.PREPARING;
		}

		void setSyncStage(SyncStage syncStage) {
			this.syncStage = syncStage;
		}

		QuorumCertificate getCommittedQC() {
			return committedQC;
		}

		@Override
		public String toString() {
			return String.format("%s{qc=%s committedQC=%s syncState=%s}", this.getClass().getSimpleName(), qc, committedQC, syncStage);
		}
	}

	private boolean requiresCommittedStateSync(SyncState syncState) {
		final BFTHeader committedHeader = syncState.committedHeader;
		if (!vertices.containsKey(committedHeader.getVertexId())) {
			View rootView = this.getRoot().getView();
			return rootView.compareTo(committedHeader.getView()) < 0;
		}

		return false;
	}

	@Override
	public void processGetVerticesRequest(GetVerticesRequest request) {
		// TODO: Handle nodes trying to DDOS this endpoint

		log.trace("SYNC_VERTICES: Received GetVerticesRequest {}", request);
		ImmutableList<VerifiedVertex> fetched = this.getVertices(request.getVertexId(), request.getCount());
		if (fetched.isEmpty()) {
			this.syncVerticesRPCSender.sendGetVerticesErrorResponse(request, this.getHighestQC(), this.getHighestCommittedQC());
			return;
		}

		log.trace("SYNC_VERTICES: Sending Response {}", fetched);
		this.syncVerticesRPCSender.sendGetVerticesResponse(request, fetched);
	}

	private void rebuildAndSyncQC(SyncState syncState) {
		log.info("SYNC_STATE: Rebuilding and syncing QC: sync={} curRoot={}", syncState, this.getRoot());

		// TODO: check if there are any vertices which haven't been local sync processed yet
		if (requiresCommittedStateSync(syncState)) {
			syncState.fetched.sort(Comparator.comparing(VerifiedVertex::getView));
			List<VerifiedVertex> nonRootVertices = syncState.fetched.stream().skip(1).collect(Collectors.toList());
			rebuild(syncState.fetched.get(0), syncState.fetched.get(1).getQC(), syncState.committedQC, nonRootVertices);
		} else {
			log.info("SYNC_STATE: skipping rebuild");
		}

		// At this point we are guaranteed to be in sync with the committed state

		if (!addQC(syncState.qc)) {
			doQCSync(syncState);
		}
	}

	@Override
	public void processCommittedStateSync(CommittedStateSync committedStateSync) {
		log.info("SYNC_STATE: synced {}", committedStateSync);

		List<Hash> syncs = committedSyncing.remove(committedStateSync.getHeader());
		if (syncs != null) {
			for (Hash syncTo : syncs) {
				SyncState syncState = syncing.get(syncTo);
				if (syncState != null) {
					rebuildAndSyncQC(syncState);
				}
			}
		}
	}

	private void processVerticesResponseForCommittedSync(Hash syncTo, SyncState syncState, GetVerticesResponse response) {
		log.info("SYNC_STATE: Processing vertices {} View {} From {}", syncState, response.getVertices().get(0).getView(), response.getSender());

		ImmutableList<BFTNode> signers = ImmutableList.of(syncState.author);
		syncState.fetched.addAll(response.getVertices());

		ledger.ifCommitSynced(syncState.committedProof)
			.then(() -> rebuildAndSyncQC(syncState))
			.elseExecuteAndSendMessageOnSync(() -> {
				syncState.setSyncStage(SyncStage.SYNC_TO_COMMIT);
				committedSyncing.compute(syncState.committedProof.getRaw(), (header, syncing) -> {
					if (syncing == null) {
						syncing = new ArrayList<>();
					}
					syncing.add(syncState.localSyncId);
					return syncing;
				});
				LocalSyncRequest localSyncRequest = new LocalSyncRequest(
					syncState.committedProof,
					signers
				);
				syncRequestSender.sendLocalSyncRequest(localSyncRequest);
			});
	}

	private void processVerticesResponseForQCSync(Hash syncTo, SyncState syncState, GetVerticesResponse response) {
		VerifiedVertex vertex = response.getVertices().get(0);
		syncState.fetched.addFirst(vertex);
		Hash nextVertexId = vertex.getParentId();

		if (vertices.containsKey(nextVertexId)) {
			for (VerifiedVertex v: syncState.fetched) {
				if (!addQC(v.getQC())) {
					log.info("GET_VERTICES failed: {}", syncState.qc);
					return;
				}
				try {
					insertVertexInternal(v);
				} catch (VertexInsertionException e) {
					log.info("GET_VERTICES failed: {}", e.getMessage());
					return;
				}
			}
			addQC(syncState.qc);
		} else {
			log.info("SYNC_VERTICES: Sending further GetVerticesRequest for qc={} fetched={} root={}",
				syncState.qc, syncState.fetched.size(), this.getRoot());
			syncVerticesRPCSender.sendGetVerticesRequest(nextVertexId, syncState.author, 1, syncTo);
		}
	}

	@Override
	public void processGetVerticesErrorResponse(GetVerticesErrorResponse response) {
		// TODO: check response

		log.info("SYNC_VERTICES: Received GetVerticesErrorResponse {} ", response);

		final Hash syncTo = (Hash) response.getOpaque();
		SyncState syncState = syncing.remove(syncTo);
		if (syncState == null) {
			return; // sync requirements already satisfied by another sync
		}

		// error response indicates that the node has moved on from last sync so try and sync to a new sync
		this.syncToQC(response.getHighestQC(), response.getHighestCommittedQC(), syncState.author);

		//this.startSync(syncTo, response.getHighestQC(), response.getHighestCommittedQC(), syncState.author);
	}

	@Override
	public void processGetVerticesResponse(GetVerticesResponse response) {
		// TODO: check response

		log.trace("SYNC_VERTICES: Received GetVerticesResponse {}", response);

		final Hash syncTo = (Hash) response.getOpaque();
		SyncState syncState = syncing.get(syncTo);
		if (syncState == null) {
			return; // sync requirements already satisfied by another sync
		}

		switch (syncState.syncStage) {
			case GET_COMMITTED_VERTICES:
				processVerticesResponseForCommittedSync(syncTo, syncState, response);
				break;
			case GET_QC_VERTICES:
				processVerticesResponseForQCSync(syncTo, syncState, response);
				break;
			default:
				throw new IllegalStateException("Unknown sync stage: " + syncState.syncStage);
		}
	}

	private void doQCSync(SyncState syncState) {
		syncState.setSyncStage(SyncStage.GET_QC_VERTICES);
		log.debug("SYNC_VERTICES: QC: Sending initial GetVerticesRequest for sync={}", syncState);
		syncVerticesRPCSender.sendGetVerticesRequest(syncState.qc.getProposed().getVertexId(), syncState.author, 1, syncState.localSyncId);
	}

	private void doCommittedSync(SyncState syncState) {
		final Hash committedQCId = syncState.getCommittedQC().getProposed().getVertexId();
		syncState.setSyncStage(SyncStage.GET_COMMITTED_VERTICES);
		log.debug("SYNC_VERTICES: Committed: Sending initial GetVerticesRequest for sync={}", syncState);
		// Retrieve the 3 vertices preceding the committedQC so we can create a valid committed root
		syncVerticesRPCSender.sendGetVerticesRequest(committedQCId, syncState.author, 3, syncState.localSyncId);
	}

	public void processLocalSync(Hash vertexId) {
		log.debug("LOCAL_SYNC: Processed {}", vertexId);
		syncing.remove(vertexId);
	}

	/**
	 * Initiate a sync to a given QC and a committedQC. Returns true if already synced
	 * otherwise will initiate a syncing process.
	 * An author is used because the author will most likely have the corresponding vertices
	 * still in memory.
	 *
	 * @param qc the qc to sync to
	 * @param committedQC the committedQC to commit sync to
	 * @param author the original author of the qc
	 * @return true if already synced, false otherwise
	 */
	public boolean syncToQC(QuorumCertificate qc, QuorumCertificate committedQC, @Nullable BFTNode author) {
		if (qc.getProposed().getView().compareTo(this.getRoot().getView()) < 0) {
			return true;
		}

		if (addQC(qc)) {
			return true;
		}

		// TODO: Move this check into pre-check
		// Bad genesis qc, ignore...
		if (qc.getView().isGenesis()) {
			log.warn("SYNC_TO_QC: Bad Genesis: {} {}", qc, committedQC);
			return false;
		}

		log.trace("SYNC_TO_QC: Need sync: {} {}", qc, committedQC);

		final Hash vertexId = qc.getProposed().getVertexId();
		if (syncing.containsKey(vertexId)) {
			// TODO: what if this committedQC is greater than the one currently in the queue
			// TODO: then should possibly replace the current one
			return false;
		}

		if (author == null) {
			throw new IllegalStateException("Syncing required but author wasn't provided.");
		}

		this.startSync(vertexId, qc, committedQC, author);

		return false;
	}

	private void startSync(Hash vertexId, QuorumCertificate qc, QuorumCertificate committedQC, BFTNode author) {
		final SyncState syncState = new SyncState(vertexId, qc, committedQC, author);
		syncing.put(vertexId, syncState);
		if (requiresCommittedStateSync(syncState)) {
			this.doCommittedSync(syncState);
		} else {
			this.doQCSync(syncState);
		}
	}

	private boolean addQC(QuorumCertificate qc) {
		if (!vertices.containsKey(qc.getProposed().getVertexId())) {
			return false;
		}

		if (highestQC.getView().compareTo(qc.getView()) < 0) {
			highestQC = qc;
			vertexStoreEventSender.highQC(qc);
		}

		qc.getCommittedAndLedgerStateProof().map(Pair::getFirst).ifPresent(header -> {
			BFTHeader highest = this.highestCommittedQC.getCommittedAndLedgerStateProof()
				.map(Pair::getFirst)
				.orElseThrow(() ->
					new IllegalStateException(String.format("Highest Committed does not have a commit: %s", this.highestCommittedQC))
				);

			if (highest.getView().compareTo(header.getView()) < 0) {
				this.highestCommittedQC = qc;
			}
		});

		return true;
	}

	private BFTHeader insertVertexInternal(VerifiedVertex vertex) throws VertexInsertionException {
		if (!vertices.containsKey(vertex.getParentId())) {
			throw new MissingParentException(vertex.getParentId());
		}

		if (!vertex.hasDirectParent()) {
			counters.increment(CounterType.BFT_INDIRECT_PARENT);
		}

		LedgerHeader ledgerHeader = ledger.prepare(vertex);

		// TODO: Don't check for state computer errors for now so that we don't
		// TODO: have to deal with failing leader proposals
		// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals
		// TODO: (also see commitVertex->storeAtom)

		vertices.put(vertex.getId(), vertex);
		updateVertexStoreSize();

		if (syncing.containsKey(vertex.getId())) {
			this.syncedVertexSender.sendSyncedVertex(vertex);
		}

		return new BFTHeader(vertex.getView(), vertex.getId(), ledgerHeader);
	}

	public BFTHeader insertVertex(VerifiedVertex vertex) throws VertexInsertionException {
		return insertVertexInternal(vertex);
	}

	/**
	 * Commit a vertex. Executes the atom and prunes the tree. Returns
	 * the Vertex if commit was successful. If the store is ahead of
	 * what is to be committed, returns an empty optional
	 *
	 * @param header the proof of commit
	 * @return the vertex if sucessful, otherwise an empty optional if vertex was already committed
	 */
	public Optional<VerifiedVertex> commit(BFTHeader header, VerifiedLedgerHeaderAndProof ledgerStateWithProof) {
		if (header.getView().compareTo(this.getRoot().getView()) <= 0) {
			return Optional.empty();
		}

		final Hash vertexId = header.getVertexId();
		final VerifiedVertex tipVertex = vertices.get(vertexId);
		if (tipVertex == null) {
			throw new IllegalStateException("Committing vertex not in store: " + header);
		}
		final LinkedList<VerifiedVertex> path = new LinkedList<>();
		VerifiedVertex vertex = tipVertex;
		while (vertex != null && !rootId.equals(vertex.getId())) {
			path.addFirst(vertex);
			vertex = vertices.remove(vertex.getParentId());
		}

		ImmutableList.Builder<Command> commandsToCommitBuilder = ImmutableList.builder();
		for (VerifiedVertex committedVertex : path) {
			this.counters.increment(CounterType.BFT_PROCESSED);
			this.vertexStoreEventSender.sendCommittedVertex(committedVertex);
			if (committedVertex.getCommand() != null) {
				commandsToCommitBuilder.add(committedVertex.getCommand());
			}
		}
		VerifiedCommandsAndProof verifiedCommandsAndProof = new VerifiedCommandsAndProof(
			commandsToCommitBuilder.build(), ledgerStateWithProof
		);
		this.ledger.commit(verifiedCommandsAndProof);

		rootId = header.getVertexId();

		updateVertexStoreSize();
		return Optional.of(tipVertex);
	}

	public List<VerifiedVertex> getPathFromRoot(Hash vertexId) {
		final List<VerifiedVertex> path = new ArrayList<>();

		VerifiedVertex vertex = vertices.get(vertexId);
		while (vertex != null && !vertex.getId().equals(rootId)) {
			path.add(vertex);
			vertex = vertices.get(vertex.getParentId());
		}

		return path;
	}

	/**
	 * Retrieves the highest committed qc in the store
	 * @return the highest committed qc
	 */
	public QuorumCertificate getHighestCommittedQC() {
		return this.highestCommittedQC;
	}

	/**
	 * Retrieves the highest qc in the store
	 * Thread-safe.
	 *
	 * @return the highest quorum certificate
	 */
	public QuorumCertificate getHighestQC() {
		return this.highestQC;
	}

	/**
	 * Retrieves list of vertices starting with the given vertexId and
	 * then proceeding to its ancestors.
	 *
	 * if the store does not contain some vertex then will return an empty
	 * list.
	 *
	 * @param vertexId the id of the vertex
	 * @param count the number of vertices to retrieve
	 * @return the list of vertices if all found, otherwise an empty list
	 */
	private ImmutableList<VerifiedVertex> getVertices(Hash vertexId, int count) {
		Hash nextId = vertexId;
		ImmutableList.Builder<VerifiedVertex> builder = ImmutableList.builderWithExpectedSize(count);
		for (int i = 0; i < count; i++) {
			VerifiedVertex vertex = this.vertices.get(nextId);
			if (vertex == null) {
				return ImmutableList.of();
			}

			builder.add(vertex);
			nextId = vertex.getParentId();
		}

		return builder.build();
	}

	public void clearSyncs() {
		committedSyncing.clear();
		syncing.clear();
	}

	public int getSize() {
		return vertices.size();
	}

	private void updateVertexStoreSize() {
		this.counters.set(CounterType.BFT_VERTEX_STORE_SIZE, this.vertices.size());
	}
}
