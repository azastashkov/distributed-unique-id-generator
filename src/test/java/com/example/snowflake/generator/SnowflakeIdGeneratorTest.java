package com.example.snowflake.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    void generatedIdsShouldBePositive() {
        var generator = new SnowflakeIdGenerator(1, 1, System::currentTimeMillis);
        for (int i = 0; i < 100; i++) {
            assertThat(generator.nextId()).isPositive();
        }
    }

    @Test
    void shouldGenerateUniqueIds() {
        var generator = new SnowflakeIdGenerator(1, 1, System::currentTimeMillis);
        Set<Long> ids = new HashSet<>();
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            ids.add(generator.nextId());
        }
        assertThat(ids).hasSize(count);
    }

    @Test
    void shouldEmbedCorrectDatacenterAndMachineIds() {
        var generator = new SnowflakeIdGenerator(17, 25, System::currentTimeMillis);
        long id = generator.nextId();
        assertThat(SnowflakeIdGenerator.extractDatacenterId(id)).isEqualTo(17);
        assertThat(SnowflakeIdGenerator.extractMachineId(id)).isEqualTo(25);
    }

    @Test
    void timestampShouldBeReasonable() {
        long before = System.currentTimeMillis();
        var generator = new SnowflakeIdGenerator(1, 1, System::currentTimeMillis);
        long id = generator.nextId();
        long after = System.currentTimeMillis();

        long timestamp = SnowflakeIdGenerator.extractTimestamp(id);
        assertThat(timestamp).isBetween(before, after);
    }

    @Test
    void sequenceShouldIncrementWithinSameMillisecond() {
        long fixedTime = System.currentTimeMillis();
        var generator = new SnowflakeIdGenerator(1, 1, () -> fixedTime);

        long id1 = generator.nextId();
        long id2 = generator.nextId();
        long id3 = generator.nextId();

        assertThat(SnowflakeIdGenerator.extractSequence(id1)).isEqualTo(0);
        assertThat(SnowflakeIdGenerator.extractSequence(id2)).isEqualTo(1);
        assertThat(SnowflakeIdGenerator.extractSequence(id3)).isEqualTo(2);
    }

    @Test
    void sequenceShouldResetOnNewMillisecond() {
        long[] time = {1000 + 1288834974657L};
        var generator = new SnowflakeIdGenerator(1, 1, () -> time[0]);

        generator.nextId(); // seq 0 at time[0]
        generator.nextId(); // seq 1 at time[0]

        time[0]++; // advance to next ms

        long id = generator.nextId();
        assertThat(SnowflakeIdGenerator.extractSequence(id)).isEqualTo(0);
    }

    @Test
    void shouldThrowOnClockRollback() {
        long[] time = {System.currentTimeMillis()};
        var generator = new SnowflakeIdGenerator(1, 1, () -> time[0]);

        generator.nextId();
        time[0] -= 10; // simulate clock going backwards

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Clock moved backwards");
    }

    @Test
    void shouldVerifyBitLayout() {
        long fixedTime = 1288834974657L + 1000L; // epoch + 1000ms
        var generator = new SnowflakeIdGenerator(0b10101, 0b01010, () -> fixedTime);

        long id = generator.nextId();

        // timestamp bits: 1000 in upper bits
        assertThat(SnowflakeIdGenerator.extractTimestamp(id)).isEqualTo(fixedTime);
        assertThat(SnowflakeIdGenerator.extractDatacenterId(id)).isEqualTo(0b10101);
        assertThat(SnowflakeIdGenerator.extractMachineId(id)).isEqualTo(0b01010);
        assertThat(SnowflakeIdGenerator.extractSequence(id)).isEqualTo(0);

        // Verify by manual bit construction
        long expected = (1000L << 22) | (0b10101L << 17) | (0b01010L << 12) | 0L;
        assertThat(id).isEqualTo(expected);
    }

    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        var generator = new SnowflakeIdGenerator(1, 1, System::currentTimeMillis);
        int threadCount = 10;
        int idsPerThread = 1000;
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicBoolean hasError = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < idsPerThread; i++) {
                        allIds.add(generator.nextId());
                    }
                } catch (Exception e) {
                    hasError.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(hasError).isFalse();
        assertThat(allIds).hasSize(threadCount * idsPerThread);
    }

    @Test
    void shouldRejectInvalidDatacenterId() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1, 0, System::currentTimeMillis))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 0, System::currentTimeMillis))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidMachineId() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0, -1, System::currentTimeMillis))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0, 32, System::currentTimeMillis))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptBoundaryValues() {
        var gen1 = new SnowflakeIdGenerator(0, 0, System::currentTimeMillis);
        assertThat(gen1.nextId()).isPositive();

        var gen2 = new SnowflakeIdGenerator(31, 31, System::currentTimeMillis);
        long id = gen2.nextId();
        assertThat(SnowflakeIdGenerator.extractDatacenterId(id)).isEqualTo(31);
        assertThat(SnowflakeIdGenerator.extractMachineId(id)).isEqualTo(31);
    }

    @Test
    void shouldHandleMaxSequencePerMillisecond() {
        long[] time = {System.currentTimeMillis()};
        var generator = new SnowflakeIdGenerator(1, 1, () -> time[0]);

        Set<Long> ids = new HashSet<>();
        // Generate 4096 IDs (max sequence per ms)
        for (int i = 0; i < 4096; i++) {
            ids.add(generator.nextId());
        }
        assertThat(ids).hasSize(4096);

        // The next one should block waiting for next ms; advance the clock
        time[0]++;
        long nextId = generator.nextId();
        assertThat(SnowflakeIdGenerator.extractSequence(nextId)).isEqualTo(0);
    }
}
