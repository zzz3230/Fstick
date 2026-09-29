package ru.fstick.installationservice.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class PaginatedResponse<T> {
    private final List<T> items;
    private final int page;
    private final int limit;
    private final int total;

    public PaginatedResponse(List<T> items, int page, int limit, int total) {
        this.items = items;
        this.page = page;
        this.limit = limit;
        this.total = total;
    }
}
