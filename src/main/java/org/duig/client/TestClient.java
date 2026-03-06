package org.duig.client;

import org.duig.generator.DuigIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Profile("test-client")
public class TestClient implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestClient.class);

    private static final int IDS_PER_SERVICE = 100;
    private static final int MAX_RETRIES = 30;
    private static final long RETRY_DELAY_MS = 2000;

    private record ServiceConfig(String name, String url, long expectedDatacenterId, long expectedMachineId) {}

    @Override
    public void run(String... args) throws Exception {
        List<ServiceConfig> services = List.of(
                new ServiceConfig("generator-1", "http://generator-1:8080", 1, 1),
                new ServiceConfig("generator-2", "http://generator-2:8080", 1, 2),
                new ServiceConfig("generator-3", "http://generator-3:8080", 2, 1)
        );

        Set<Long> allIds = new HashSet<>();
        int totalExpected = services.size() * IDS_PER_SERVICE;

        for (ServiceConfig service : services) {
            log.info("Testing {} at {}", service.name, service.url);

            RestClient client = RestClient.builder()
                    .baseUrl(service.url)
                    .build();

            waitForService(client, service.name);

            List<Long> ids = fetchIds(client, IDS_PER_SERVICE);

            for (long id : ids) {
                assert id > 0 : "ID must be positive, got: " + id;

                long datacenterId = DuigIdGenerator.extractDatacenterId(id);
                long machineId = DuigIdGenerator.extractMachineId(id);
                long timestamp = DuigIdGenerator.extractTimestamp(id);

                assert datacenterId == service.expectedDatacenterId :
                        String.format("Expected datacenter %d but got %d for service %s",
                                service.expectedDatacenterId, datacenterId, service.name);
                assert machineId == service.expectedMachineId :
                        String.format("Expected machine %d but got %d for service %s",
                                service.expectedMachineId, machineId, service.name);

                long now = System.currentTimeMillis();
                assert Math.abs(now - timestamp) < 60_000 :
                        "Timestamp too far from current time: " + timestamp;

                allIds.add(id);
            }

            log.info("{}: {} IDs validated OK", service.name, ids.size());
        }

        assert allIds.size() == totalExpected :
                String.format("Expected %d unique IDs across all services, got %d",
                        totalExpected, allIds.size());

        log.info("All tests passed! Total unique IDs: {}/{}", allIds.size(), totalExpected);
    }

    private void waitForService(RestClient client, String serviceName) throws InterruptedException {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                client.get().uri("/api/id").retrieve().body(Map.class);
                log.info("{} is ready", serviceName);
                return;
            } catch (Exception e) {
                log.info("Waiting for {}... ({}/{})", serviceName, i + 1, MAX_RETRIES);
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
        throw new RuntimeException("Service " + serviceName + " did not become ready");
    }

    @SuppressWarnings("unchecked")
    private List<Long> fetchIds(RestClient client, int count) {
        List<Map<String, Object>> response = client.get()
                .uri("/api/id/bulk?count=" + count)
                .retrieve()
                .body(List.class);

        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> entry : response) {
            ids.add(((Number) entry.get("id")).longValue());
        }
        return ids;
    }
}
