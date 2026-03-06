package org.duig.controller;

import org.duig.generator.DuigIdGenerator;
import org.duig.model.IdResponse;
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

    private final DuigIdGenerator generator;

    public IdController(DuigIdGenerator generator) {
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
                DuigIdGenerator.extractTimestamp(id),
                DuigIdGenerator.extractDatacenterId(id),
                DuigIdGenerator.extractMachineId(id),
                DuigIdGenerator.extractSequence(id)
        );
    }
}
