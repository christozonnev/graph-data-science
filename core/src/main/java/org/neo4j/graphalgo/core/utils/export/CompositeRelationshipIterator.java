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
package org.neo4j.graphalgo.core.utils.export;

import org.neo4j.graphalgo.core.huge.AdjacencyList;
import org.neo4j.graphalgo.core.huge.AdjacencyOffsets;
import org.neo4j.internal.batchimport.input.InputEntityVisitor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class CompositeRelationshipIterator {

    private final AdjacencyList adjacencyList;
    private final AdjacencyOffsets adjacencyOffsets;

    private final Map<String, AdjacencyList> propertyLists;
    private final Map<String, AdjacencyOffsets> propertyOffsets;

    private final AdjacencyList.DecompressingCursor cursorCache;
    private final Map<String, AdjacencyList.Cursor> propertyCursorCache;

    CompositeRelationshipIterator(
        AdjacencyList adjacencyList,
        AdjacencyOffsets adjacencyOffsets,
        Map<String, AdjacencyList> propertyLists,
        Map<String, AdjacencyOffsets> propertyOffsets
    ) {
        this.adjacencyList = adjacencyList;
        this.adjacencyOffsets = adjacencyOffsets;
        this.propertyLists = propertyLists;
        this.propertyOffsets = propertyOffsets;

        // create un-initialized cursors
        this.cursorCache = adjacencyList.rawDecompressingCursor();
        this.propertyCursorCache = new HashMap<>(propertyLists.size());
        this.propertyLists.forEach((propertyKey, propertyList) -> this.propertyCursorCache.put(
            propertyKey,
            propertyList.rawCursor()
        ));
    }

    CompositeRelationshipIterator concurrentCopy() {
        return new CompositeRelationshipIterator(adjacencyList, adjacencyOffsets, propertyLists, propertyOffsets);
    }

    int propertyCount() {
        return propertyLists.size();
    }

    void forEachRelationship(long sourceId, String relType, InputEntityVisitor visitor) throws IOException {
        var offset = adjacencyOffsets.get(sourceId);

        if (offset == 0L) {
            return;
        }

        // init adjacency cursor
        var adjacencyCursor = AdjacencyList.decompressingCursor(cursorCache, offset);
        // init property cursors
        for (var propertyKey : propertyLists.keySet()) {
            propertyCursorCache.put(
                propertyKey,
                AdjacencyList.cursor(
                    propertyCursorCache.get(propertyKey),
                    propertyOffsets.get(propertyKey).get(sourceId)
                )
            );
        }

        // in-step iteration of adjacency and property cursors
        while (adjacencyCursor.hasNextVLong()) {
            long targetId = adjacencyCursor.nextVLong();
            visitor.startId(sourceId);
            visitor.endId(targetId);
            visitor.type(relType);

            for (var propertyKeyAndCursor : propertyCursorCache.entrySet()) {
                visitor.property(
                    propertyKeyAndCursor.getKey(),
                    Double.longBitsToDouble(propertyKeyAndCursor.getValue().nextLong())
                );
            }

            visitor.endOfEntity();
        }
    }
}
