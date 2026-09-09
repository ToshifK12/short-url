package com.toshif.shorturl.hashing;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {

    @Test
    void routesConsistentlyToTheSameNodeForTheSameKey() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(100);
        ring.addNode("shard-a");
        ring.addNode("shard-b");

        String first = ring.route("abc123");
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, ring.route("abc123"));
        }
    }

    @Test
    void distributesKeysReasonablyEvenlyAcrossShards() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(200);
        ring.addNode("shard-a");
        ring.addNode("shard-b");
        ring.addNode("shard-c");

        Map<String, Integer> dist = ring.distribution(60_000);
        assertEquals(3, dist.size());
        dist.values().forEach(count -> {
            double pct = count / 600.0;
            assertTrue(pct > 20 && pct < 45, "Shard should get a reasonable share of keys, got " + pct + "%");
        });
    }

    @Test
    void addingAShardOnlyRemapsAMinorityOfExistingKeys() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(150);
        ring.addNode("shard-a");
        ring.addNode("shard-b");
        ring.addNode("shard-c");

        int sampleSize = 20_000;
        String[] before = new String[sampleSize];
        for (int i = 0; i < sampleSize; i++) {
            before[i] = ring.route("key-" + i);
        }

        ring.addNode("shard-d");

        int moved = 0;
        for (int i = 0; i < sampleSize; i++) {
            if (!ring.route("key-" + i).equals(before[i])) {
                moved++;
            }
        }

        double movedPct = 100.0 * moved / sampleSize;
        assertTrue(movedPct < 40, "Expected roughly 1/(n+1) of keys to move, moved " + movedPct + "%");
    }

    @Test
    void throwsWhenRoutingWithNoNodes() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(10);
        assertThrows(IllegalStateException.class, () -> ring.route("anything"));
    }

    @Test
    void removingANodeRedistributesItsKeysToRemainingNodes() {
        ConsistentHashRing<String> ring = new ConsistentHashRing<>(150);
        ring.addNode("shard-a");
        ring.addNode("shard-b");
        ring.addNode("shard-c");

        ring.removeNode("shard-b");
        assertEquals(2, ring.nodes().size());
        for (int i = 0; i < 1000; i++) {
            assertNotEquals("shard-b", ring.route("key-" + i));
        }
    }
}
