package org.duig.model;

public record IdResponse(
        long id,
        String idHex,
        long timestamp,
        long datacenterId,
        long machineId,
        long sequence
) {
}
