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

package com.radixdlt.client.lib;

import org.json.JSONArray;
import org.json.JSONObject;

import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static com.radixdlt.utils.functional.Optionals.allOf;

import static java.util.Optional.ofNullable;

public class NodeClient {
	private static final MediaType MEDIA_TYPE = MediaType.parse("application/json");

	private final AtomicInteger counter = new AtomicInteger();

	private final String baseUrl;
	private final OkHttpClient client;
	private AtomicReference<Byte> magicHolder = new AtomicReference<>();

	private NodeClient(String baseUrl) {
		this.baseUrl = sanitize(baseUrl);
		this.client = new OkHttpClient.Builder()
			.connectionSpecs(List.of(ConnectionSpec.CLEARTEXT))
			.connectTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.pingInterval(30, TimeUnit.SECONDS)
			.build();
	}

	private static String sanitize(String baseUrl) {
		if (baseUrl.endsWith("/")) {
			return baseUrl.substring(0, baseUrl.length() - 1);
		} else {
			return baseUrl;
		}
	}

	public static Result<NodeClient> create(String baseUrl) {
		if (baseUrl == null) {
			return Result.fail("Base URL is mandatory");
		}

		return Result.ok(new NodeClient(baseUrl)).flatMap(NodeClient::tryConnect);
	}

	public Result<List<TokenBalance>> callTokenBalances(ECPublicKey publicKey) {
		var params = jsonArray().put(toAddress(publicKey).toString());

		return call("tokenBalances", params)
			.map(this::parseTokenBalances);
	}

	private RadixAddress toAddress(ECPublicKey publicKey) {
		return new RadixAddress(magicHolder.get(), publicKey);
	}

	public Result<JSONObject> call(String method, JSONArray params) {
		return performCall(wrap(method, params))
			.flatMap(this::parseJson);
	}

	private Result<NodeClient> tryConnect() {
		var params = jsonArray();

		return call("networkId", params)
			.map(obj -> obj.getJSONObject("result"))
			.flatMap(obj -> Result.fromOptional(ofNullable(obj.opt("networkId")), "Network ID not found"))
			.filter(Integer.class::isInstance, "Network ID is not an integer")
			.map(Integer.class::cast)
			.onSuccess(magic -> magicHolder.set(magic.byteValue()))
			.map(__ -> this);
	}

	private JSONObject wrap(String method, JSONArray params) {
		return jsonObject()
			.put("jsonrpc", "2.0")
			.put("method", "radix." + method)
			.put("id", counter.incrementAndGet())
			.put("params", params);
	}

	private List<TokenBalance> parseTokenBalances(JSONObject json) {
		return ofNullable(json.optJSONArray("tokenBalances"))
			.map(this::parseTokenBalanceEntries)
			.orElseGet(List::of);
	}

	private List<TokenBalance> parseTokenBalanceEntries(JSONArray array) {
		var list = new ArrayList<TokenBalance>();

		array.forEach(
			obj ->
				ifIsA(
					obj, JSONObject.class,
					object -> allOf(rri(object), uint384(object, "amount"))
						.map(TokenBalance::create).ifPresent(list::add)
				)
		);

		return list;
	}

	private static <T> void ifIsA(Object obj, Class<T> clazz, Consumer<T> consumer) {
		if (clazz.isInstance(obj)) {
			consumer.accept(clazz.cast(obj));
		}
	}

	private Result<JSONObject> parseJson(String text) {
		return Result.wrap(() -> new JSONObject(text));
	}

	private static Optional<String> string(JSONObject object, String name) {
		return ofNullable(object.optString(name));
	}

	private static Optional<Rri> rri(JSONObject object) {
		return string(object, "rri").flatMap(value -> Rri.fromSpecString(value).toOptional());
	}

	private static Optional<UInt384> uint384(JSONObject object, String name) {
		return string(object, name).flatMap(value -> UInt384.fromString(value).toOptional());
	}

	private Result<String> performCall(JSONObject json) {
		var body = RequestBody.create(MEDIA_TYPE, json.toString());
		var request = new Request.Builder().url(baseUrl + "/rpc").post(body).build();

		try (var response = client.newCall(request).execute(); var responseBody = response.body()) {
			return responseBody != null
				   ? Result.ok(responseBody.string())
				   : Result.fail("No content in response");
		} catch (IOException e) {
			return Result.fail(e);
		}
	}
}
