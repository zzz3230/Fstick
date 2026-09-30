package ru.fstick.installationservice;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import ru.fstick.installationservice.dto.response.BranchChatsResponse;
import ru.fstick.installationservice.dto.response.InstallationBranchResponse;
import ru.fstick.installationservice.dto.response.InstallationShortResponse;
import ru.fstick.installationservice.dto.response.ResolveResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonContractTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void namingStrategyIsSnakeCase() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        }

        assertEquals("SNAKE_CASE", properties.getProperty("spring.jackson.property-naming-strategy"));
    }

    @Test
    void listItemKeysAreSnakeCase() {
        InstallationShortResponse item = new InstallationShortResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "WORKING",
                UUID.randomUUID(), UUID.randomUUID(), OffsetDateTime.now());

        assertEquals(
                Set.of("installation_id", "plugin_id", "branch_id", "branch_status",
                        "author_id", "installed_by", "installed_at"),
                keys(item));
    }

    @Test
    void patchAndInternalResponsesKeysAreSnakeCase() {
        assertEquals(Set.of("installation_id", "branch_id", "branch_status"),
                keys(new InstallationBranchResponse(UUID.randomUUID(), UUID.randomUUID(), "RELEASED")));
        assertEquals(Set.of("branch_id", "branch_status"),
                keys(new ResolveResponse(UUID.randomUUID(), "RELEASED")));
        assertEquals(Set.of("chat_ids"), keys(new BranchChatsResponse(List.of("!a:x"))));
    }

    private Set<String> keys(Object value) {
        JsonNode node = mapper.valueToTree(value);
        Set<String> names = new TreeSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
