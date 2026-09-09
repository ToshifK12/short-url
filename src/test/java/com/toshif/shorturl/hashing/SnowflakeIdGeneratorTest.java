package com.toshif.shorturl.hashing;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    void generatesStrictlyIncreasingIdsOnASingleNode() {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(1);
        long prev = -1;
        for (int i = 0; i < 10_000; i++) {
            long id = gen.nextId();
            assertTrue(id > prev);
            prev = id;
        }
    }

    @Test
    void rejectsOutOfRangeNodeIds() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1024));
    }

    @Test
    void neverCollidesAcrossDifferentNodesEvenUnderConcurrency() throws InterruptedException {
        int nodes = 4;
        int idsPerNode = 20_000;
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(nodes);

        for (int n = 0; n < nodes; n++) {
            SnowflakeIdGenerator gen = new SnowflakeIdGenerator(n);
            pool.submit(() -> {
                for (int i = 0; i < idsPerNode; i++) {
                    allIds.add(gen.nextId());
                }
            });
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(nodes * idsPerNode, allIds.size());
    }
}
