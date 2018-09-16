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

package edu.byu.ece.rapidSmith.router.wireCluster;

import edu.byu.ece.rapidSmith.device.Tile;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * This class can store data about each wire on the FPGA in O(1)
 */
public class WireDataMap<T> {

	/**
	 * The data store.
	 *
	 * We do not use an array to map from wire to data, since there are too many different wires.
	 * TODO: Evaluate if we could use one map per tile type to map from wire id to index in a compact array that would get stored here
	 * TODO: Evaluate array of all wires as first level, tile map as second
	 */
	private Map<Tile, Map<Integer, T>> data = new HashMap<>();

	/**
	 * Get the value currently stored in the Map
	 *
	 * @param t the tile
	 * @param wire the wire
	 * @return The element currently stored, or null if there is none
	 */
	public T get(Tile t, int wire) {
		Map<Integer, T> tileMap = data.get(t);
		if (tileMap == null) {
			return null;
		}
		return tileMap.get(wire);
	}

	public boolean containsKey(Tile t, int wire) {
		Map<Integer, T> tileMap = data.get(t);
		if (tileMap == null) {
			return false;
		}
		return tileMap.containsKey(wire);
	}

	/**
	 * Store a new value in the map
	 *
	 * @param t the tile
	 * @param wire the wire
	 * @param data the data to store
	 * @return the previous value, or null if there is none
	 */
	public T put(Tile t, int wire, T data) {
		Map<Integer, T> tileMap = this.data.computeIfAbsent(t, x -> new HashMap<>());
		return tileMap.put(wire, data);
	}

	/**
	 * Clear all data
	 */
	public void clear() {
		data.clear();
	}

	T computeIfAbsent(Tile t, int wire, BiFunction<? super Tile, ? super Integer, ? extends T> mappingFunction) {
		Map<Integer, T> tileMap = this.data.get(t);
		//No compute if absent, seems to be slow...
		if (tileMap == null) {
			tileMap = new HashMap<>();
			this.data.put(t, tileMap);
		}
		T res = tileMap.get(wire);
		//No compute if absent, seems to be slow...
		if (res == null) {
			res = mappingFunction.apply(t, wire);
			tileMap.put(wire, res);
		}
		return res;
	}

	public Stream<T> stream() {
		return data.values().stream().flatMap(x -> x.values().stream());
	}

}
