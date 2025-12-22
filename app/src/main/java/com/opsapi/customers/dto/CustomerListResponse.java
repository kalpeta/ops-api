package com.opsapi.customers.dto;

import java.util.List;

public class CustomerListResponse {
    private final int limit;
    private final int offset;
    private final int count;
    private final List<CustomerResponse> items;

    public CustomerListResponse(int limit, int offset, int count, List<CustomerResponse> items) {
        this.limit = limit;
        this.offset = offset;
        this.count = count;
        this.items = items;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public int getCount() {
        return count;
    }

    public List<CustomerResponse> getItems() {
        return items;
    }
}