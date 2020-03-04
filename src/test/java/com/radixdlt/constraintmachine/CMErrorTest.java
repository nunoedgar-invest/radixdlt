package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.crypto.Hash;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class CMErrorTest {
	@Test
	public void equalsContract() {

		ConstraintMachine.CMValidationState state0 = new ConstraintMachine.CMValidationState(
				Hash.ZERO_HASH,
				ImmutableMap.of()
		);

		ConstraintMachine.CMValidationState state1 = new ConstraintMachine.CMValidationState(
				Hash.random(),
				ImmutableMap.of()
		);

		EqualsVerifier.forClass(CMError.class)
				.withPrefabValues(ConstraintMachine.CMValidationState.class, state0, state1)
				.verify();
	}


}
