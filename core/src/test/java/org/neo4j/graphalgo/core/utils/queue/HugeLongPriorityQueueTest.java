/*
 * Copyright (c) 2017-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.core.utils.queue;

import io.qala.datagen.RandomShortApi;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static io.qala.datagen.RandomShortApi.integer;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HugeLongPriorityQueueTest {

    @Test
    void testIsEmpty() {
        var capacity = integer(10, 20);
        var queue = HugeLongPriorityQueue.min(capacity);
        assertEquals(queue.size(), 0);
    }

    @Test
    void testClear() {
        var maxSize = integer(3, 10);
        var queue = HugeLongPriorityQueue.min(maxSize);
        var count = integer(3, maxSize);
        for (long element = 0; element < count; element++) {
            queue.add(element, integer(1, 5));
        }
        assertEquals(queue.size(), count);
        queue.clear();
        assertEquals(queue.size(), 0);
    }

    @Test
    void testAdd() {
        var size = 50;
        var count = integer(5, size);
        var queue = HugeLongPriorityQueue.min(size);
        var minElement = -1L;
        var minCost = Double.POSITIVE_INFINITY;

        for (long key = 0; key < count; key++) {
            double weight = exclusiveDouble(0D, 100D);
            if (weight < minCost) {
                minCost = weight;
                minElement = key;
            }
            queue.add(key, weight);
            assertEquals(queue.top(), minElement);
        }
    }

    @Test
    void testAddAndPop() {
        var size = 50;
        var queue = HugeLongPriorityQueue.min(size);
        var elements = new ArrayList<Pair<Long, Double>>();
        var count = integer(5, size);
        var minElement = -1L;
        var minCost = Double.POSITIVE_INFINITY;

        for (long element = 1; element <= count; element++) {
            var weight = exclusiveDouble(0D, 100D);
            if (weight < minCost) {
                minCost = weight;
                minElement = element;
            }
            queue.add(element, weight);
            assertEquals(queue.top(), minElement);
            elements.add(Tuples.pair(element, weight));
        }

        // PQ isn't stable for duplicate elements, so we have to
        // test those with non strict ordering requirements
        var byCost = elements
            .stream()
            .collect(Collectors.groupingBy(
                Pair::getTwo,
                Collectors.mapping(Pair::getOne, Collectors.toSet())
            ));
        var costGroups = byCost
            .keySet()
            .stream()
            .sorted()
            .collect(Collectors.toList());

        for (var cost : costGroups) {
            var allowedElements = byCost.get(cost);
            while (!allowedElements.isEmpty()) {
                long item = queue.pop();
                assertThat(allowedElements, hasItem(item));
                allowedElements.remove(item);
            }
        }

        assertTrue(queue.isEmpty());
    }

    @Test
    void testUpdateDecreasing() {
        var size = 50;
        var queue = HugeLongPriorityQueue.min(size);

        var count = integer(5, size);
        var minCost = Double.POSITIVE_INFINITY;
        for (long element = 1; element <= count; element++) {
            double weight = exclusiveDouble(50D, 100D);
            if (weight < minCost) {
                minCost = weight;
            }
            queue.add(element, weight);
        }

        for (long element = count; element >= 1; element--) {
            minCost = Math.nextDown(minCost);
            queue.addCost(element, minCost);
            queue.update(element);
            assertEquals(element, queue.top());
        }
    }

    @Test
    void testUpdateIncreasing() {
        var size = 50;
        var queue = HugeLongPriorityQueue.min(size);
        int count = integer(5, size);
        double maxCost = Double.NEGATIVE_INFINITY;

        for (long element = 1; element <= count; element++) {
            var weight = exclusiveDouble(50D, 100D);
            if (weight > maxCost) {
                maxCost = weight;
            }
            queue.add(element, weight);
        }

        var top = queue.top();
        for (var element = count; element >= 1; element--) {
            if (element == top) {
                continue;
            }
            maxCost = Math.nextUp(maxCost);
            queue.addCost(element, maxCost);
            queue.update(element);
            assertEquals(top, queue.top());
        }
    }

    @Test
    void testUpdateNotExisting() {
        var size = 50;
        var queue = HugeLongPriorityQueue.min(size);
        var count = integer(5, size);

        for (long element = 1; element <= count; element++) {
            queue.add(element, exclusiveDouble(50D, 100D));
        }

        var top = queue.top();
        for (long element = count + 1; element < count + 10; element++) {
            queue.addCost(element, 1D);
            queue.update(element);
            assertEquals(top, queue.top());
        }
    }

    private double exclusiveDouble(double exclusiveMin, double exclusiveMax) {
        return RandomShortApi.Double(Math.nextUp(exclusiveMin), exclusiveMax);
    }
}