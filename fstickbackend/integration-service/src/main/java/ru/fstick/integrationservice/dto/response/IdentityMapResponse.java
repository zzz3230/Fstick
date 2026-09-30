package ru.fstick.integrationservice.dto.response;

import java.util.Map;

public record IdentityMapResponse<K, V>(Map<K, V> map) {
}
