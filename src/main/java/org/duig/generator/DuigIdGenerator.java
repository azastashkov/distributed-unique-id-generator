package org.duig.generator;

import org.duig.config.DuigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

@Component
@EnableConfigurationProperties(DuigProperties.class)
public class DuigIdGenerator {

    private static final long EPOCH = 1288834974657L;

    private static final int DATACENTER_ID_BITS = 5;
    private static final int MACHINE_ID_BITS = 5;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_ID_BITS) - 1;
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private static final int MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final int DATACENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;

    private final long datacenterId;
    private final long machineId;
    private final LongSupplier clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    @Autowired
    public DuigIdGenerator(DuigProperties properties) {
        this(properties.datacenterId(), properties.machineId(), System::currentTimeMillis);
    }

    DuigIdGenerator(long datacenterId, long machineId, LongSupplier clock) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    "Datacenter ID must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                    "Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
        this.clock = clock;
    }

    public synchronized long nextId() {
        long timestamp = clock.getAsLong();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "Clock moved backwards. Refusing to generate ID for "
                            + (lastTimestamp - timestamp) + " milliseconds");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = clock.getAsLong();
        while (timestamp <= lastTimestamp) {
            timestamp = clock.getAsLong();
        }
        return timestamp;
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public long getMachineId() {
        return machineId;
    }

    public static long extractTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + EPOCH;
    }

    public static long extractDatacenterId(long id) {
        return (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    public static long extractMachineId(long id) {
        return (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
    }

    public static long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }
}
