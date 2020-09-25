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
package org.neo4j.graphalgo.knn;

import org.neo4j.graphalgo.AlgoBaseProc;
import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.StatsProc;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.core.utils.ProgressTimer;
import org.neo4j.graphalgo.nodesim.SimilarityGraphResult;
import org.neo4j.graphalgo.result.AbstractResultBuilder;
import org.neo4j.graphalgo.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.graphalgo.knn.KnnWriteProc.computeToGraph;
import static org.neo4j.graphalgo.nodesim.NodeSimilarityProc.computeHistogram;
import static org.neo4j.graphalgo.nodesim.NodeSimilarityProc.shouldComputeHistogram;
import static org.neo4j.procedure.Mode.READ;

public final class KnnStatsProc extends StatsProc<Knn, Knn.Result, KnnStatsProc.StatsResult, KnnStatsConfig> {

    @Procedure(name = "gds.beta.knn.stats", mode = READ)
    @Description(STATS_DESCRIPTION)
    public Stream<StatsResult> stats(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return stats(compute(graphNameOrConfig, configuration));
    }

    @Procedure(value = "gds.beta.knn.stats.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimateStats(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected KnnStatsConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return KnnStatsConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    @Override
    protected AlgorithmFactory<Knn, KnnStatsConfig> algorithmFactory() {
        return new KnnFactory<>();
    }

    @Override
    protected AbstractResultBuilder<StatsResult> resultBuilder(AlgoBaseProc.ComputationResult<Knn, Knn.Result, KnnStatsConfig> computeResult) {
        throw new UnsupportedOperationException("Knn handles result building individually.");
    }

    @Override
    public Stream<StatsResult> stats(AlgoBaseProc.ComputationResult<Knn, Knn.Result, KnnStatsConfig> computationResult) {
        return runWithExceptionLogging("Graph stats failed", () -> {
            KnnStatsConfig config = computationResult.config();

            if (computationResult.isGraphEmpty()) {
                return Stream.of(
                    new KnnStatsProc.StatsResult(
                        computationResult.createMillis(),
                        0,
                        0,
                        0,
                        0,
                        Collections.emptyMap(),
                        config.toMap()
                    )
                );
            }
            Knn algorithm = Objects.requireNonNull(computationResult.algorithm());
            var result = Objects.requireNonNull(computationResult.result());

            KnnProc.KnnResultBuilder<StatsResult> resultBuilder =
                KnnProc.resultBuilder(new StatsResult.Builder(), computationResult);

            if (shouldComputeHistogram(callContext)) {
                try (ProgressTimer ignored = resultBuilder.timePostProcessing()) {
                    SimilarityGraphResult similarityGraphResult = computeToGraph(
                        computationResult.graph(),
                        algorithm.nodeCount(),
                        config.concurrency(),
                        result,
                        algorithm.context()
                    );

                    Graph similarityGraph = similarityGraphResult.similarityGraph();

                    resultBuilder
                        .withHistogram(computeHistogram(similarityGraph))
                        .withNodesCompared(similarityGraphResult.comparedNodes())
                        .withRelationshipsWritten(similarityGraph.relationshipCount());
                }
            } else {
                resultBuilder
                    .withNodesCompared(algorithm.nodeCount())
                    .withRelationshipsWritten(result.totalSimilarityPairs());
            }

            return Stream.of(resultBuilder.build());
        });
    }

    public static final class StatsResult {

        public long createMillis;
        public long computeMillis;
        public long postProcessingMillis;

        public long nodesCompared;
        public long similarityPairs;
        public Map<String, Object> similarityDistribution;
        public Map<String, Object> configuration;

        StatsResult(
            long createMillis,
            long computeMillis,
            long postProcessingMillis,
            long nodesCompared,
            long similarityPairs,
            Map<String, Object> communityDistribution,
            Map<String, Object> configuration

        ) {
            this.createMillis = createMillis;
            this.computeMillis = computeMillis;
            this.postProcessingMillis = postProcessingMillis;
            this.nodesCompared = nodesCompared;
            this.similarityPairs = similarityPairs;
            this.similarityDistribution = communityDistribution;
            this.configuration = configuration;
        }

        static class Builder extends KnnProc.KnnResultBuilder<KnnStatsProc.StatsResult> {

            @Override
            public KnnStatsProc.StatsResult build() {
                return new KnnStatsProc.StatsResult(
                    createMillis,
                    computeMillis,
                    postProcessingMillis,
                    nodesCompared,
                    relationshipsWritten,
                    distribution(),
                    config.toMap()
                );
            }
        }
    }
}