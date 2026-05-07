package ru.fstick.installationservice.dto.response;

import java.util.List;

public class PaginatedResponse<T> {

    private List<T> data;
    private int page;
    private int limit;
    private int totalCount;
    private boolean hasNext;

    public PaginatedResponse(List<T> data, int page, int limit, int totalCount) {
        this.data = data;
        this.page = page;
        this.limit = limit;
        this.totalCount = totalCount;
        this.hasNext = (long) page * limit < totalCount;
    }

    public List<T> getData() { return data; }
    public int getPage() { return page; }
    public int getLimit() { return limit; }
    public int getTotalCount() { return totalCount; }
    public boolean isHasNext() { return hasNext; }
}