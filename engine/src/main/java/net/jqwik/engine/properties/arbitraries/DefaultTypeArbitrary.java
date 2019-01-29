package net.jqwik.engine.properties.arbitraries;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import net.jqwik.api.*;
import net.jqwik.api.arbitraries.*;
import net.jqwik.api.providers.*;
import net.jqwik.engine.support.*;

public class DefaultTypeArbitrary<T> extends OneOfArbitrary<T> implements TypeArbitrary<T> {

	private TypeUsage targetType;
	private final List<Executable> creators = new ArrayList<>();

	public DefaultTypeArbitrary(Class<T> targetClass) {
		this(TypeUsage.of(targetClass));
	}

	public DefaultTypeArbitrary(TypeUsage targetType) {
		super(Collections.emptyList());
		this.targetType = targetType;
	}

	@Override
	public TypeArbitrary<T> use(Executable creator) {
		checkCreator(creator);
		addArbitrary(createArbitrary(creator));
		return this;
	}

	private void checkCreator(Executable creator) {
		checkReturnType(creator);

		if (creator instanceof Method) {
			checkMethod((Method) creator);
		}

		if (creator instanceof Constructor) {
			checkConstructor((Constructor) creator);
		}
	}

	private void checkReturnType(Executable creator) {
		TypeUsage returnType = TypeUsage.forType(creator.getAnnotatedReturnType().getType());
		if (!returnType.canBeAssignedTo(targetType)) {
			throw new JqwikException(String.format("Creator %s should return type %s", creator, targetType));
		}
	}

	private void checkMethod(Method method) {
		if (!JqwikReflectionSupport.isStatic(method)) {
			throw new JqwikException(String.format("Method %s should be static", method));
		}
	}

	private void checkConstructor(Constructor method) {
	}

	@Override
	public RandomGenerator<T> generator(int genSize) {
		if (arbitraries().isEmpty()) {
			String message = String.format("TypeArbitrary<%s> has no arbitraries to choose from.", targetType);
			throw new JqwikException(message);
		}
		return super.generator(genSize);
	}

	private Arbitrary<T> createArbitrary(Executable creator) {
		List<Arbitrary<Object>> parameterArbitraries =
			Arrays.stream(creator.getAnnotatedParameterTypes())
				  .map(annotatedType -> Arbitraries.defaultFor(TypeUsage.forType(annotatedType.getType())))
				  .collect(Collectors.toList());

		Function<List<Object>, T> combinator = paramList -> combinator(creator).apply(paramList.toArray());
		return Combinators.combine(parameterArbitraries).as(combinator);
	}

	private Function<Object[], T> combinator(Executable creator) {
		if (creator instanceof Method) {
			return combinatorForMethod((Method) creator);
		}
		if (creator instanceof Constructor) {
			return combinatorForConstructor((Constructor) creator);
		}
		throw new JqwikException(String.format("Creator %s is not supported", creator));
	}

	private Function<Object[], T> combinatorForMethod(Method method) {
		method.setAccessible(true);
		return params -> generateNext(params, p -> method.invoke(null, p));
	}

	private Function<Object[], T> combinatorForConstructor(Constructor constructor) {
		constructor.setAccessible(true);
		return params -> generateNext(params, p -> constructor.newInstance(p));
	}

	private T generateNext(Object[] params, Combinator combinator) {
		long count = 0;
		while (count++ <= 1000) {
			try {
				//noinspection unchecked
				return (T) combinator.combine(params);
			} catch (Throwable ignored) {
			}
		}
		String message = String.format("TypeArbitrary<%s>: Trying to generate object failed too often", targetType);
		throw new JqwikException(message);
	}

	@FunctionalInterface
	private interface Combinator {
		Object combine(Object[] params) throws Throwable;
	}

}
