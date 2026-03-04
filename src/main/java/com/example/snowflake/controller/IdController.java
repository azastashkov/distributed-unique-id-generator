package com.example.snowflake.controller;

import com.example.snowflake.generator.SnowflakeIdGenerator;
import com.example.snowflake.model.IdResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/id")
public class IdController {

    private static final int DEFAULT_COUNT = 10;
    private static final int MAX_COUNT = 1000;

    private final SnowflakeIdGenerator generator;

    public IdController(SnowflakeIdGenerator generator) {
        this.generator = generator;
    }

    @GetMapping
    public IdResponse generateId() {
        return toResponse(generator.nextId());
    }

    @GetMapping("/bulk")
    public List<IdResponse> generateBulk(
            @RequestParam(defaultValue = "" + DEFAULT_COUNT) int count) {
        int effective = Math.clamp(count, 1, MAX_COUNT);
        return IntStream.range(0, effective)
                .mapToObj(i -> toResponse(generator.nextId()))
                .toList();
    }

    private IdResponse toResponse(long id) {
        return new IdResponse(
                id,
                Long.toHexString(id),
                SnowflakeIdGenerator.extractTimestamp(id),
                SnowflakeIdGenerator.extractDatacenterId(id),
                SnowflakeIdGenerator.extractMachineId(id),
                SnowflakeIdGenerator.extractSequence(id)
        );
    }
}
