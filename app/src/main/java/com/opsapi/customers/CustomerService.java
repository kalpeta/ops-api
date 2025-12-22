package com.opsapi.customers;

import com.opsapi.customers.dto.CustomerCreateRequest;
import com.opsapi.customers.dto.CustomerListResponse;
import com.opsapi.customers.dto.CustomerResponse;
import com.opsapi.customers.dto.CustomerUpdateRequest;
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

    public CustomerResponse update(UUID id, CustomerUpdateRequest req) {
        CustomerEntity entity = repo.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        // Update fields (normalize like create)
        entity.setName(req.getName().trim());
        entity.setEmail(req.getEmail().trim().toLowerCase());

        CustomerEntity saved = repo.save(entity);
        return toResponse(saved);
    }

    public void delete(UUID id) {
        boolean exists = repo.existsById(id);
        if (!exists) {
            throw new CustomerNotFoundException(id);
        }
        repo.deleteById(id);
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