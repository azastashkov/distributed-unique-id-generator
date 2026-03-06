package org.duig.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duig")
public record DuigProperties(long datacenterId, long machineId) {
}
