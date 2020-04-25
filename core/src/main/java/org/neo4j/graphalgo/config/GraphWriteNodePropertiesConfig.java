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
package org.neo4j.graphalgo.config;

import org.immutables.value.Value;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.loading.GraphStore;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.neo4j.graphalgo.ElementProjection.PROJECT_ALL;
import static org.neo4j.graphalgo.utils.StringJoining.join;

@ValueClass
@Configuration("GraphWriteNodePropertiesConfigImpl")
@SuppressWarnings("immutables:subtype")
public interface GraphWriteNodePropertiesConfig extends WriteConfig {

    @Configuration.Parameter
    List<String> nodeProperties();

    @Configuration.Parameter
    @Value.Default
    @Override
    default List<String> nodeLabels() {
        return singletonList(PROJECT_ALL);
    }

    // This is necessary because of the initialization order in the generated constructors.
    // If we don't set it, it uses into this.concurrency, which is not initialized yet.
    @Value.Default
    @Override
    default int writeConcurrency() {
        return DEFAULT_CONCURRENCY;
    }

    static GraphWriteNodePropertiesConfig of(
        String userName,
        String graphName,
        List<String> nodeProperties,
        List<String> nodeLabels,
        CypherMapWrapper config
    ) {
        return new GraphWriteNodePropertiesConfigImpl(
            nodeProperties,
            nodeLabels,
            Optional.of(graphName),
            Optional.empty(),
            userName,
            config
        );
    }

    @Configuration.Ignore
    default void validate(GraphStore graphStore) {
        if (!nodeLabels().contains(PROJECT_ALL)) {
            // validate that all given labels have all the properties
            nodeLabelIdentifiers(graphStore).forEach(nodeLabel ->
                nodeProperties().forEach(nodeProperty -> {
                    if (!graphStore.hasNodeProperty(singletonList(nodeLabel), nodeProperty)) {
                        throw new IllegalArgumentException(String.format(
                            "Node projection '%s' does not have property key '%s'. Available keys: %s.",
                            nodeLabel.name,
                            nodeProperty,
                            join(graphStore.nodePropertyKeys(nodeLabel))
                        ));
                    }
                }));
        } else {
            // validate that at least one label has all the properties
            boolean hasValidLabel = nodeLabelIdentifiers(graphStore).stream()
                .anyMatch(nodeLabel -> nodeProperties().stream()
                    .allMatch(nodeProperty -> graphStore.hasNodeProperty(singletonList(nodeLabel), nodeProperty)));

            if (!hasValidLabel) {
                throw new IllegalArgumentException(String.format(
                    "No node projection with all property keys %s found.",
                    join(nodeProperties())
                ));
            }
        }
    }

    /**
     * Returns the node labels that are to be considered for writing properties.
     *
     * If nodeLabels contains '*`, this returns all node labels in the graph store
     * that have the specified nodeProperties.
     *
     * Otherwise, it just returns all the labels in the graph store since validation
     * made sure that all node labels have the specified properties.
     */
    @Configuration.Ignore
    default Collection<NodeLabel> validNodeLabels(GraphStore graphStore) {
        Collection<NodeLabel> filteredNodeLabels;

        if (nodeLabels().contains(PROJECT_ALL)) {
            // Filter node labels that have all the properties.
            // Validation guarantees that there is at least one.
            filteredNodeLabels = nodeLabelIdentifiers(graphStore)
                .stream()
                .filter(nodeLabel -> graphStore.nodePropertyKeys(nodeLabel).containsAll(nodeProperties()))
                .collect(Collectors.toList());
        } else {
            // Write for all the labels that are specified.
            // Validation guarantees that each label has all properties.
            filteredNodeLabels = nodeLabelIdentifiers(graphStore);
        }

        return filteredNodeLabels;
    }
}