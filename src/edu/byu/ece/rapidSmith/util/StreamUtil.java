/*
 * _______________________________________________________________________________
 *
 *  Copyright (c) 2012 TU Dresden, Chair for Embedded Systems
 *  Copyright (c) 2013-2016 TU Darmstadt, Computer Systems Group
 *  (http://www.rs.tu-darmstadt.de) All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *  3. All advertising materials mentioning features or use of this software
 *     must display the following acknowledgement: "This product includes
 *     software developed by the TU Dresden Chair for Embedded Systems, TU Darmstadt Computer Systems Group and
 *     its contributors."
 *
 *  4. Neither the name of the TU Dresden Chair for Embedded Systems, TU Darmstadt Computer Systems Group nor the
 *     names of its contributors may be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY TU DRESDEN CHAIR FOR EMBEDDED SYSTEMS, TU DARMSTADT COMPUTER SYSTEMS GROUP AND
 *  CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 *  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 *  TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * _______________________________________________________________________________
 */
package edu.byu.ece.rapidSmith.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Various utility functions
 *
 * Created by jakobw on 09.11.15.
 */
public final class StreamUtil {

	private StreamUtil() {

	}

	private static <K, V, M extends Map<K, V>> BinaryOperator<M> mapMerger(BinaryOperator<V> mergeFunction) {
		return (m1, m2) -> {
			for (Map.Entry<K, V> e : m2.entrySet()) {
				m1.merge(e.getKey(), e.getValue(), mergeFunction);
			}
			return m1;
		};
	}


	public static <T, K> Collector<T, ?, Map<K, List<T>>> multiGroupingBy(Function<? super T, Stream<K>> classifier) {
		return multiGroupingBy(classifier, Collectors.toList());
	}

	public static <T, K, A, D> Collector<T, ?, Map<K, D>> multiGroupingBy(Function<? super T, Stream<K>> classifier, Collector<? super T, A, D> downstream) {
		return multiGroupingBy(classifier, HashMap::new, downstream);
	}

	/**
	 * Group by multiple keys
	 * @param classifier the group to sort an element into
	 * @param mapFactory creator of the maps
	 * @param downstream downstream collector
	 * @param <T> element type
	 * @param <K> key type
	 * @param <D> downstream collector result type
	 * @param <A> downstream supplier type
	 * @param <M> map type
	 * @return the elements grouped by applying the classifier
	 */
	public static <T, K, D, A, M extends Map<K, D>> Collector<T, ?, M> multiGroupingBy(Function<? super T, Stream<K>> classifier, Supplier<M> mapFactory, Collector<? super T, A, D> downstream) {
		Supplier<A> downstreamSupplier = downstream.supplier();
		BiConsumer<A, ? super T> downstreamAccumulator = downstream.accumulator();
		BiConsumer<Map<K, A>, T> accumulator = (m, t) -> {
			Stream<K> keys = Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key");
			keys.forEach(key -> {
				A container = m.computeIfAbsent(key, k -> downstreamSupplier.get());
				downstreamAccumulator.accept(container, t);
			});
		};
		BinaryOperator<Map<K, A>> merger = StreamUtil.<K, A, Map<K, A>>mapMerger(downstream.combiner());
		@SuppressWarnings("unchecked")
		Supplier<Map<K, A>> mangledFactory = (Supplier<Map<K, A>>) mapFactory;

		if (downstream.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH)) {
			Collector<T, ?, Map<K, A>> c = Collector.of(mangledFactory, accumulator, merger, Collector.Characteristics.IDENTITY_FINISH);
			return (Collector<T, ?, M>) c;
		} else {
			@SuppressWarnings("unchecked")
			Function<A, A> downstreamFinisher = (Function<A, A>) downstream.finisher();
			Function<Map<K, A>, M> finisher = intermediate -> {
				intermediate.replaceAll((k, v) -> downstreamFinisher.apply(v));
				@SuppressWarnings("unchecked")
				M castResult = (M) intermediate;
				return castResult;
			};
			return Collector.of(mangledFactory, accumulator, merger, finisher);
		}
	}

	/**
	 * Make a stream from an initial value and a function to get to the next value
	 * @param initial Initial value
	 * @param getNext Function to get the next value from an element
	 * @param <T> element type
	 * @return stream of elements
	 */
	public static <T> Stream<T> stream(T initial, UnaryOperator<T> getNext) {
		Iterator<T> it = new Iterator<T>() {
			T current = initial;

			@Override
			public boolean hasNext() {
				return current != null;
			}

			@Override
			public T next() {
				T c = current;
				current = getNext.apply(current);
				return c;
			}
		};
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);

	}
}
