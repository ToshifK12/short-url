package com.toshif.shorturl.hashing;

/**
 * Twitter Snowflake-style 64-bit ID generator.
 *
 * Layout (MSB to LSB):
 *   1 bit  unused (sign)
 *  41 bits milliseconds since a custom epoch (~69 years of range)
 *  10 bits node id (0-1023)             -> up to 1024 app instances
 *  12 bits per-millisecond sequence     -> up to 4096 ids/ms/node
 *
 * This is what lets shortening stay horizontally scalable: every app
 * instance mints unique IDs locally with no shared counter, no coordination,
 * and no single point of contention -- which was the whole point of not
 * using a single Redis INCR as the ID source once there's more than one
 * app instance behind the load balancer.
 */
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1_704_067_200_000L; // 2024-01-01T00:00:00Z
    private static final long NODE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static final long NODE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID);
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "Clock moved backwards; refusing to generate id for " + (lastTimestamp - timestamp) + "ms");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted for this millisecond, spin to the next one.
                while (timestamp <= lastTimestamp) {
                    timestamp = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }
}
