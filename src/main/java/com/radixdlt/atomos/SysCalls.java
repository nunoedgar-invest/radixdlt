package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.WitnessValidator;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Exposes the interface which application particle constraints/transitions can be programmed against.
 */
public interface SysCalls {
	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to a destination the particle will be stored in
	 */
	<T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck
	);

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to a destination the particle will be stored in
	 */
	<T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	);

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to the destinations a particle will be stored in
	 */
	<T extends Particle> void registerParticleMultipleAddress(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck
	);

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to the destinations a particle will be stored in
	 */
	<T extends Particle> void registerParticleMultipleAddress(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	);

	/**
	 * Defines a valid transition in the constraint machine as well as the
	 * requirements for executing that transition.
	 *
	 * @param inputClass class of the input particle
	 * @param outputClass class of the output particle
	 * @param procedure procedure which gets executed on the constraint machine
	 * @param witnessValidator validation which defines who can execute this transition
	 * @param <T> input particle type
	 * @param <U> output particle type
	 */
	<T extends Particle, U extends Particle> void createTransition(
		Class<T> inputClass,
		Class<U> outputClass,
		TransitionProcedure<T, U> procedure,
		WitnessValidator<T, U> witnessValidator
	);

	/**
	 * Creates a new resource globally identifiable by an RRI.
	 */
	<T extends Particle> void createTransitionFromRRI(Class<T> outputClass);

	/**
	 * Creates a new resource globally identifiable by an RRI.
	 */
	<T extends Particle, U extends Particle> void createTransitionFromRRICombined(
		Class<T> outputClass0,
		Class<U> outputClass1,
		BiPredicate<T, U> combinedCheck
	);
}
