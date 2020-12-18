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

package com.radixdlt.sanitytestsuite.scenario.keygen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.reflect.TypeToken;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.sanitytestsuite.scenario.SanityTestScenarioRunner;
import com.radixdlt.sanitytestsuite.scenario.hashing.HashingTestVector;
import com.radixdlt.utils.Bytes;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;

public final class KeyGenTestScenarioRunner extends SanityTestScenarioRunner<KeyGenTestVector> {


	public String testScenarioIdentifier() {
		return "secp256k1";
	}

	@Override
	public TypeReference<KeyGenTestVector> testVectorTypeReference() {
		return new TypeReference<KeyGenTestVector>() {};
	}

	public void doRunTestVector(KeyGenTestVector testVector) throws AssertionError {
		ECPublicKey publicKey = null;
		ECPublicKey expectedPublicKey = null;

		try {
			publicKey = ECKeyPair.fromPrivateKey(Bytes.fromHexString(testVector.input.privateKey)).getPublicKey();
			expectedPublicKey = ECPublicKey.fromBytes(Bytes.fromHexString(testVector.expected.uncompressedPublicKey));
		} catch (Exception e) {
			throw new AssertionError("Failed to create PublicKeys", e);
		}

		assertNotNull(publicKey);
		assertTrue(publicKey.equals(expectedPublicKey));

	}
}
