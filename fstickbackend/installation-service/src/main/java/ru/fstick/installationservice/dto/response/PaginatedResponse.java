package ru.fstick.installationservice.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class PaginatedResponse<T> {
    private final List<T> data;
    private final int page;
    private final int limit;
    private final int totalCount;
    private final boolean hasNext;

    public PaginatedResponse(List<T> data, int page, int limit, int totalCount) {
        this.data = data;
        this.page = page;
        this.limit = limit;
        this.totalCount = totalCount;
        this.hasNext = (long) page * limit < totalCount;
    }
}