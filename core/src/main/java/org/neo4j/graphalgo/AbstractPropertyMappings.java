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
package org.neo4j.graphalgo;

import org.immutables.builder.Builder.AccessibleFields;
import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;
import org.neo4j.graphalgo.annotation.DataClass;
import org.neo4j.graphalgo.core.DeduplicationStrategy;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonMap;

@DataClass
@Value.Immutable(singleton = true)
public abstract class AbstractPropertyMappings implements Iterable<PropertyMapping> {

    public abstract List<PropertyMapping> mappings();

    public static PropertyMappings of(PropertyMapping... mappings) {
        if (mappings == null) {
            return PropertyMappings.of();
        }
        return PropertyMappings.of(Arrays.asList(mappings));
    }

    public static PropertyMappings fromObject(Object relPropertyMapping) {
        if (relPropertyMapping instanceof PropertyMappings) {
            return (PropertyMappings) relPropertyMapping;
        }
        if (relPropertyMapping instanceof String) {
            String propertyMapping = (String) relPropertyMapping;
            return fromObject(singletonMap(propertyMapping, propertyMapping));
        } else if (relPropertyMapping instanceof List) {
            PropertyMappings.Builder builder = PropertyMappings.builder();
            for (Object mapping : (List<?>) relPropertyMapping) {
                builder.addAllMappings(fromObject(mapping).mappings());
            }
            return builder.build();
        } else if (relPropertyMapping instanceof Map) {
            PropertyMappings.Builder builder = PropertyMappings.builder();
            ((Map<String, Object>) relPropertyMapping).forEach((key, spec) -> {
                PropertyMapping propertyMapping = PropertyMapping.fromObject(key, spec);
                builder.addMapping(propertyMapping);
            });
            return builder.build();
        } else {
            throw new IllegalArgumentException(String.format(
                "Expected String or Map for property mappings. Got %s.",
                relPropertyMapping.getClass().getSimpleName()
            ));
        }
    }

    public static PropertyMappings of(ResolvedPropertyMappings resolvedPropertyMappings) {
        return of(resolvedPropertyMappings
            .mappings()
            .stream()
            .map(PropertyMapping::of)
            .toArray(PropertyMapping[]::new));
    }

    public Stream<PropertyMapping> stream() {
        return mappings().stream();
    }

    public Optional<PropertyMapping> head() {
        return stream().findFirst();
    }

    @Override
    public Iterator<PropertyMapping> iterator() {
        return mappings().iterator();
    }

    @Deprecated
    public Optional<Double> defaultWeight() {
        return stream().mapToDouble(PropertyMapping::defaultValue).boxed().findFirst();
    }

    public boolean hasMappings() {
        return !mappings().isEmpty();
    }

    public int numberOfMappings() {
        return mappings().size();
    }

    public Map<String, Object> toObject(boolean includeAggregation) {
        return stream()
            .map(mapping -> mapping.toObject(includeAggregation))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); },
                LinkedHashMap::new
            ));
    }

    public @Nullable Object toMinimalObject(boolean includeAggregation) {
        List<PropertyMapping> mappings = mappings();
        if (mappings.isEmpty()) {
            return null;
        }
        if (mappings.size() == 1) {
            Object mappingObject = mappings.get(0).toMinimalObject(includeAggregation, true);
            if (mappingObject instanceof String) {
                return mappingObject;
            }
            if (mappingObject instanceof Map.Entry) {
                Map.Entry<?, ?> object = (Map.Entry<?, ?>) mappingObject;
                return singletonMap(String.valueOf(object.getKey()), object.getValue());
            }
            return null;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        for (PropertyMapping mapping : mappings) {
            Object mappingObject = mapping.toMinimalObject(includeAggregation, false);
            if (mappingObject instanceof Map.Entry) {
                Map.Entry<?, ?> object = (Map.Entry<?, ?>) mappingObject;
                properties.put(String.valueOf(object.getKey()), object.getValue());
            }
        }
        return properties;
    }

    public PropertyMappings mergeWith(PropertyMappings other) {
        if (!hasMappings()) {
            return other;
        }
        if (!other.hasMappings()) {
            return PropertyMappings.copyOf(this);
        }
        Builder builder = PropertyMappings.builder();
        builder.addMappings(Stream.concat(stream(), other.stream()).distinct());
        return builder.build();
    }

    @Value.Check
    void checkForAggregationMixing() {
        long noneStrategyCount = stream()
            .filter(d -> d.deduplicationStrategy() == DeduplicationStrategy.NONE)
            .count();

        if (noneStrategyCount > 0 && noneStrategyCount < numberOfMappings()) {
            throw new IllegalArgumentException(
                "Conflicting relationship property deduplication strategies, it is not allowed to mix `NONE` with aggregations.");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @AccessibleFields
    public static final class Builder extends PropertyMappings.Builder {

        private DeduplicationStrategy deduplicationStrategy;

        Builder() {
            deduplicationStrategy = DeduplicationStrategy.DEFAULT;
        }

        void addMappings(Stream<? extends PropertyMapping> propertyMappings) {
            Objects.requireNonNull(propertyMappings, "propertyMappings must not be null.");
            propertyMappings.forEach(this::addMapping);
        }

        void addOptionalMapping(PropertyMapping mapping) {
            Objects.requireNonNull(mapping, "Given UnresolvedPropertyMapping must not be null.");
            if (mapping.hasValidName()) {
                addMapping(mapping);
            }
        }

        public void addOptionalMappings(PropertyMapping... propertyMappings) {
            Objects.requireNonNull(propertyMappings, "propertyMappings must not be null.");
            for (PropertyMapping propertyMapping : propertyMappings) {
                addOptionalMapping(propertyMapping);
            }
        }

        public void addOptionalMappings(Stream<? extends PropertyMapping> propertyMappings) {
            Objects.requireNonNull(propertyMappings, "propertyMappings must not be null.");
            propertyMappings.forEach(this::addOptionalMapping);
        }

        public void setGlobalDeduplicationStrategy(DeduplicationStrategy deduplicationStrategy) {
            this.deduplicationStrategy = Objects.requireNonNull(deduplicationStrategy, "deduplicationStrategy must not be empty");
        }

        @Override
        public PropertyMappings build() {
            if (deduplicationStrategy != DeduplicationStrategy.DEFAULT && mappings != null) {
                for (ListIterator<PropertyMapping> iter = mappings.listIterator(); iter.hasNext(); ) {
                    PropertyMapping mapping = iter.next().setNonDefaultAggregation(deduplicationStrategy);
                    iter.set(mapping);
                }
            }
            return super.build();
        }
    }
}
