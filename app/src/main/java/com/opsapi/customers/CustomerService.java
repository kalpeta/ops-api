package com.opsapi.customers;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerListResponse;
import com.opsapi.customers.dto.CustomerResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository repo;
    private final CustomerQueryRepository queryRepo;

    public CustomerService(CustomerRepository repo, CustomerQueryRepository queryRepo) {
        this.repo = repo;
        this.queryRepo = queryRepo;
    }

    public CustomerResponse create(CustomerCreateRequest req) {
        UUID id = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity(
                id,
                req.getName().trim(),
                req.getEmail().trim().toLowerCase()
        );

        CustomerEntity saved = repo.save(entity);
        return toResponse(saved);
    }

    public CustomerResponse getById(UUID id) {
        CustomerEntity entity = repo.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        return toResponse(entity);
    }

    public CustomerListResponse list(int limit, int offset) {
        List<CustomerEntity> entities = queryRepo.listNewestFirst(limit, offset);

        List<CustomerResponse> items = entities.stream()
                .map(this::toResponse)
                .toList();

        return new CustomerListResponse(limit, offset, items.size(), items);
    }

    private CustomerResponse toResponse(CustomerEntity entity) {
        return new CustomerResponse(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}