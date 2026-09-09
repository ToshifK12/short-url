package com.toshif.shorturl.hashing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A textbook consistent-hashing ring with virtual nodes.
 *
 * Each physical node is hashed into {@code virtualNodesPerNode} points on a
 * 32-bit ring (derived from MD5). Looking up a key walks clockwise from the
 * key's hash to the first node point. This is what lets the shard tier scale
 * horizontally: adding or removing a shard only remaps ~1/N of the keys
 * instead of rehashing the whole keyspace, which is the property that makes
 * this preferable to {@code hash(key) % numShards} for a cache/storage layer
 * that grows over time.
 *
 * Thread-safe: reads are lock-free-ish (read lock), writes (add/remove node)
 * take the write lock. Node membership changes are expected to be rare
 * (deploy-time), lookups happen on every request.
 */
public class ConsistentHashRing<T> {

    private final SortedMap<Long, T> ring = new TreeMap<>();
    private final int virtualNodesPerNode;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ConsistentHashRing(int virtualNodesPerNode) {
        if (virtualNodesPerNode < 1) {
            throw new IllegalArgumentException("virtualNodesPerNode must be >= 1");
        }
        this.virtualNodesPerNode = virtualNodesPerNode;
    }

    public void addNode(T node) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < virtualNodesPerNode; i++) {
                ring.put(hash(node.toString() + "#VN" + i), node);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeNode(T node) {
        lock.writeLock().lock();
        try {
            for (int i = 0; i < virtualNodesPerNode; i++) {
                ring.remove(hash(node.toString() + "#VN" + i));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the node responsible for the given key, walking clockwise
     * from the key's position on the ring.
     */
    public T route(String key) {
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                throw new IllegalStateException("Consistent hash ring has no nodes");
            }
            long hash = hash(key);
            SortedMap<Long, T> tail = ring.tailMap(hash);
            Long targetKey = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
            return ring.get(targetKey);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return ring.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<T> nodes() {
        lock.readLock().lock();
        try {
            return java.util.Set.copyOf(ring.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reports how many keys out of {@code sampleSize} synthetic keys land on
     * each node, for debugging/verifying distribution balance in tests.
     */
    public Map<T, Integer> distribution(int sampleSize) {
        Map<T, Integer> counts = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        for (int i = 0; i < sampleSize; i++) {
            T node = route("sample-key-" + i);
            counts.merge(node, 1, Integer::sum);
        }
        return counts;
    }

    private long hash(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(key.getBytes(StandardCharsets.UTF_8));
            // Use the first 4 bytes as an unsigned 32-bit value spread across a long ring position.
            return ((long) (digest[0] & 0xFF) << 24)
                    | ((long) (digest[1] & 0xFF) << 16)
                    | ((long) (digest[2] & 0xFF) << 8)
                    | ((long) (digest[3] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
