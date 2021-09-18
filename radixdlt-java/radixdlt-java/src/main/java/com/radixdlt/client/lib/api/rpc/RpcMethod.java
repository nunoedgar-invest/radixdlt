package com.radixdlt.client.lib.api.rpc;

import static com.radixdlt.client.lib.api.rpc.EndPoint.*;

public enum RpcMethod {
	TOKEN_NATIVE("tokens.get_native_token", TOKENS),
	TOKEN_INFO("tokens.get_info", TOKENS),

	ACCOUNT_BALANCES("account.get_balances", ACCOUNTS),
	ACCOUNT_HISTORY("account.get_transaction_history", ACCOUNTS),
	ACCOUNT_STAKES("account.get_stake_positions", ACCOUNTS),
	ACCOUNT_UNSTAKES("account.get_unstake_positions", ACCOUNTS),

	TRANSACTION_LOOKUP("transactions.lookup_transaction", TRANSACTIONS),
	TRANSACTION_STATUS("transactions.get_transaction_status", TRANSACTIONS),

	NETWORK_ID("network.get_id", NETWORK),
	NETWORK_THROUGHPUT("network.get_throughput", NETWORK),
	NETWORK_DEMAND("network.get_demand", NETWORK),

	VALIDATORS_LIST("validators.get_next_epoch_set", ACCOUNTS),
	VALIDATORS_LOOKUP("validators.lookup_validator", ACCOUNTS),

	CONSTRUCTION_BUILD("construction.build_transaction", CONSTRUCTION),
	CONSTRUCTION_FINALIZE("construction.finalize_transaction", CONSTRUCTION),
	CONSTRUCTION_SUBMIT("construction.submit_transaction", CONSTRUCTION),

	NETWORK_CONFIG("networking.get_configuration", SYSTEM_NODE),
	NETWORK_PEERS("networking.get_peers", SYSTEM_NODE),
	NETWORK_DATA("networking.get_data", SYSTEM_NODE),
	NETWORK_ADDRESS_BOOK("networking.get_address_book", SYSTEM_NODE),

	TRANSACTION_LIST("get_transactions", TRANSACTIONS_NODE),

	API_CONFIGURATION("api.get_configuration", SYSTEM_NODE),
	API_DATA("api.get_data", SYSTEM_NODE),

	BFT_CONFIGURATION("bft.get_configuration", SYSTEM_NODE),
	BFT_DATA("bft.get_data", SYSTEM_NODE),

	MEMPOOL_CONFIGURATION("mempool.get_configuration", SYSTEM_NODE),
	MEMPOOL_DATA("mempool.get_data", SYSTEM_NODE),

	LEDGER_PROOF("ledger.get_latest_proof", SYSTEM_NODE),
	LEDGER_EPOCH_PROOF("ledger.get_latest_epoch_proof", SYSTEM_NODE),
	LEDGER_CHECKPOINTS("checkpoints.get_checkpoints", SYSTEM_NODE),

	RADIX_ENGINE_CONFIGURATION("radix_engine.get_configuration", SYSTEM_NODE),
	RADIX_ENGINE_DATA("radix_engine.get_data", SYSTEM_NODE),

	SYNC_CONFIGURATION("sync.get_configuration", SYSTEM_NODE),
	SYNC_DATA("sync.get_data", SYSTEM_NODE),

	VALIDATION_NODE_INFO("validation.get_node_info", VALIDATION_NODE),
	VALIDATION_CURRENT_EPOCH("validation.get_current_epoch_data", VALIDATION_NODE),

	ACCOUNT_INFO("account.get_info", ACCOUNT_NODE),
	ACCOUNT_SUBMIT_SINGLE_STEP("account.submit_transaction_single_step", ACCOUNT_NODE);

	private final String method;
	private final EndPoint endPoint;

	RpcMethod(String method, EndPoint endPoint) {
		this.method = method;
		this.endPoint = endPoint;
	}

	public String method() {
		return method;
	}

	public EndPoint endPoint() {
		return endPoint;
	}
}
