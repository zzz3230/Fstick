package ru.gatewayservice.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IdentityClient {

    private final RestClient restClient;

    @Autowired
    public IdentityClient(@Value("${services.integration.base-url}") String integrationBaseUrl) {
        this(RestClient.builder().baseUrl(integrationBaseUrl).build());
    }

    IdentityClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public UUID resolve(String mxid) {
        try {
            ResolveResponse response = restClient.post()
                    .uri("/api/v1/identity/resolve")
                    .body(Map.of("mxid", mxid))
                    .retrieve()
                    .body(ResolveResponse.class);
            if (response == null || response.internalUuid == null) {
                throw new IdentityUnavailableException("Empty identity response", null);
            }
            return response.internalUuid;
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new IllegalArgumentException("Invalid user id", ex);
        } catch (RestClientException ex) {
            throw new IdentityUnavailableException("Identity service is unavailable", ex);
        }
    }

    public Map<UUID, String> lookupBatch(Collection<UUID> uuids) {
        try {
            LookupResponse response = restClient.post()
                    .uri("/api/v1/identity/lookup-batch")
                    .body(Map.of("uuids", List.copyOf(uuids)))
                    .retrieve()
                    .body(LookupResponse.class);
            return response == null || response.map == null ? Map.of() : response.map;
        } catch (RestClientException ex) {
            throw new IdentityUnavailableException("Identity service is unavailable", ex);
        }
    }

    static final class ResolveResponse {
        @JsonProperty("internal_uuid")
        UUID internalUuid;

        @JsonProperty("created")
        boolean created;
    }

    static final class LookupResponse {
        @JsonProperty("map")
        Map<UUID, String> map = new HashMap<>();
    }
}
