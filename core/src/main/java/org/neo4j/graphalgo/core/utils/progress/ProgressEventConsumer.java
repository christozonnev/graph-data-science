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
package org.neo4j.graphalgo.core.utils.progress;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.neo4j.graphalgo.compat.JobPromise;
import org.neo4j.graphalgo.compat.JobRunner;
import org.neo4j.graphalgo.compat.Neo4jProxy;
import org.neo4j.scheduler.Group;
import org.neo4j.scheduler.JobScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyMap;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.neo4j.graphalgo.core.utils.RenamesCurrentThread.renameThread;

final class ProgressEventConsumer implements Runnable, ProgressEventStore {

    private final Monitor monitor;
    private final JobRunner jobRunner;
    private final Queue<LogEvent> queue;

    private volatile @Nullable JobPromise job;
    private final Map<String, Map<String, List<LogEvent>>> events;

    ProgressEventConsumer(
        Monitor monitor,
        JobScheduler jobScheduler,
        Queue<LogEvent> queue
    ) {
        this(monitor, Neo4jProxy.runnerFromScheduler(jobScheduler, Group.DATA_COLLECTOR), queue);
    }

    @TestOnly
    ProgressEventConsumer(
        JobRunner jobRunner,
        Queue<LogEvent> queue
    ) {
        this(Monitor.EMPTY, jobRunner, queue);
    }

    private ProgressEventConsumer(
        Monitor monitor,
        JobRunner jobRunner,
        Queue<LogEvent> queue
    ) {
        this.monitor = monitor;
        this.jobRunner = jobRunner;
        this.queue = queue;
        events = new ConcurrentHashMap<>();
    }

    @Override
    public List<LogEvent> query(String username) {
        return events
            .getOrDefault(username, emptyMap())
            .values()
            .stream()
            .filter(not(List::isEmpty))
            .map(items -> items.get(items.size() - 1))
            .collect(toList());
    }

    @Override
    public void run() {
        try (var ignored = renameThread("progress-event-consumer")) {
            LogEvent event;
            while ((event = queue.poll()) != null && !Thread.interrupted()) {
                process(event);
            }
        }
    }

    private void process(LogEvent event) {
        events
            .computeIfAbsent(event.username(), __ -> new ConcurrentHashMap<>())
            .computeIfAbsent(event.id(), __ -> new ArrayList<>())
            .add(event);
    }

    void start() {
        monitor.started();
        if (job != null) {
            throw new IllegalArgumentException("Already running");
        }
        job = jobRunner.scheduleAtInterval(this, 0, 100, TimeUnit.MILLISECONDS);
    }

    void stop() {
        monitor.stopped();
        // pull into field to avoid racing against another stop.
        // We might cancel multiple times, but we're not running into a sneaky NPE
        var job = this.job;
        if (job == null) {
            throw new IllegalArgumentException("Not running");
        }
        job.cancel();
        this.job = null;
    }

    @TestOnly
    boolean isRunning() {
        return job != null;
    }

    interface Monitor {
        void started();

        void stopped();

        class Adapter implements Monitor {
            @Override
            public void started() {
            }

            @Override
            public void stopped() {
            }
        }

        Monitor EMPTY = new Monitor.Adapter();
    }
}
