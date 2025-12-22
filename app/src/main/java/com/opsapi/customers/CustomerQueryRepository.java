package com.opsapi.customers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CustomerQueryRepository {

    private final EntityManager em;

    public CustomerQueryRepository(EntityManager em) {
        this.em = em;
    }

    public List<CustomerEntity> listNewestFirst(int limit, int offset) {
        // JPQL (JPA Query Language): query CustomerEntity objects, not table rows directly.
        // We order by createdAt so pagination is stable/predictable.
        TypedQuery<CustomerEntity> q = em.createQuery(
                "SELECT c FROM CustomerEntity c ORDER BY c.createdAt DESC",
                CustomerEntity.class
        );

        // The key to real offset/limit pagination:
        q.setFirstResult(offset); // skip 'offset' rows
        q.setMaxResults(limit);   // return up to 'limit' rows

        return q.getResultList();
    }
}