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
package org.neo4j.graphalgo.api;

import org.jetbrains.annotations.Nullable;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.annotation.ValueClass;

import java.util.Optional;

@ValueClass
public interface Relationships {

    TopologyCSR topology();

    Optional<PropertyCSR> properties();

    static Relationships of(
        long relationshipCount,
        Orientation orientation,
        AdjacencyList adjacencyList,
        AdjacencyOffsets adjacencyOffsets,
        @Nullable AdjacencyList properties,
        @Nullable AdjacencyOffsets propertyOffsets,
        double defaultPropertyValue
    ) {
        TopologyCSR topologyCSR = ImmutableTopologyCSR.of(adjacencyList, adjacencyOffsets, relationshipCount, orientation);

        Optional<PropertyCSR> maybePropertyCSR = properties != null && propertyOffsets != null
            ? Optional.of(ImmutablePropertyCSR.of(
                properties,
                propertyOffsets,
                relationshipCount,
                orientation,
                defaultPropertyValue
            )) : Optional.empty();

        return ImmutableRelationships.of(topologyCSR, maybePropertyCSR);
    }

    @ValueClass
    interface TopologyCSR {
        AdjacencyList list();

        AdjacencyOffsets offsets();

        long elementCount();

        Orientation orientation();
    }

    @ValueClass
    @SuppressWarnings("immutables:subtype")
    interface PropertyCSR extends TopologyCSR {
        double defaultPropertyValue();
    }
}
