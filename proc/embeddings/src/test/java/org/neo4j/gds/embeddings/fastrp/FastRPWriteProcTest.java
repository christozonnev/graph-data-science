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
package org.neo4j.gds.embeddings.fastrp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphalgo.AlgoBaseProc;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.WritePropertyConfigTest;
import org.neo4j.graphalgo.core.CypherMapWrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FastRPWriteProcTest extends FastRPProcTest<FastRPWriteConfig>
    implements WritePropertyConfigTest<FastRP, FastRPWriteConfig, FastRP.FastRPResult> {

    @Override
    GdsCypher.ExecutionModes mode() {
        return GdsCypher.ExecutionModes.WRITE;
    }

    @Override
    public Class<? extends AlgoBaseProc<FastRP, FastRP.FastRPResult, FastRPWriteConfig>> getProcedureClazz() {
        return FastRPWriteProc.class;
    }

    @Override
    public FastRPWriteConfig createConfig(CypherMapWrapper userInput) {
        return FastRPWriteConfig.of(getUsername(), Optional.empty(), Optional.empty(), userInput);
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper userInput) {
        CypherMapWrapper minimalConfig = super.createMinimalConfig(userInput);

        if (!minimalConfig.containsKey("writeProperty")) {
            return minimalConfig.withString("writeProperty", "embedding");
        }
        return minimalConfig;
    }

    @ParameterizedTest
    @MethodSource("org.neo4j.gds.embeddings.fastrp.FastRPProcTest#weights")
    void shouldComputeNonZeroEmbeddings(List<Float> weights) {
        int embeddingDimension = 128;
        GdsCypher.ParametersBuildStage queryBuilder = GdsCypher.call()
            .withNodeLabel("Node")
            .withRelationshipType("REL", Orientation.UNDIRECTED)
            .algo("fastRP")
            .writeMode()
            .addParameter("embeddingDimension", embeddingDimension)
            .addParameter("writeProperty", "embedding");

        if (!weights.isEmpty()) {
            queryBuilder.addParameter("iterationWeights", weights);
        }
        String writeQuery = queryBuilder.yields();

        runQuery(writeQuery);

        runQueryWithRowConsumer("MATCH (n:Node) RETURN n.embedding as embedding", row -> {
            float[] embeddings = (float[]) row.get("embedding");
            assertEquals(embeddingDimension, embeddings.length);
            boolean allMatch = true;
            for (float embedding : embeddings) {
                if (Float.compare(embedding, 0.0F) != 0) {
                    allMatch = false;
                    break;
                }
            }
            assertFalse(allMatch);
        });
    }

    @Test
    void shouldComputeAndWriteWithWeight() {
        int embeddingDimension = 128;

        String query = GdsCypher.call()
            .withNodeLabel("Node")
            .withNodeLabel("Node2")
            .withRelationshipType("REL2")
            .withRelationshipProperty("weight")
            .algo("fastRP")
            .writeMode()
            .addParameter("embeddingDimension", embeddingDimension)
            .addParameter("relationshipWeightProperty", "weight")
            .addParameter("writeProperty", "embedding")
            .yields();

        runQuery(query);

        String retrieveQuery = "MATCH (n) WHERE n:Node OR n:Node2 RETURN n.name as name, n.embedding as embedding";
        Map<String, float[]> embeddings = new HashMap<>(3);
        runQueryWithRowConsumer(retrieveQuery, row -> {
            embeddings.put(row.getString("name"), (float[]) row.get("embedding"));
        });

        for (int i = 0; i < 128; i++) {
            assertEquals(embeddings.get("b")[i], embeddings.get("e")[i] * 2);
        }
    }
}
