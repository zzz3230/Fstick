package ru.fstick.registry_service.dto.api;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaginationData {
    private int page;
    private int limit;
    private int total;
    private int totalPages;
    private boolean hasPrev;
    private boolean hasNext;
}
