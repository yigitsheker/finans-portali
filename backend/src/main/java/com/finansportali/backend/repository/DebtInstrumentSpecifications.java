package com.finansportali.backend.repository;

import com.finansportali.backend.entity.DebtInstrument;
import com.finansportali.backend.entity.DebtInstrumentType;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JPA Specifications for dynamic DebtInstrument queries.
 * This approach avoids the PostgreSQL bytea casting issue with null parameters.
 */
public class DebtInstrumentSpecifications {

    public static Specification<DebtInstrument> withFilters(
            DebtInstrumentType type,
            String currency,
            LocalDate maturityFrom,
            LocalDate maturityTo,
            String search) {
        
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Always filter by active = true
            predicates.add(criteriaBuilder.isTrue(root.get("active")));
            
            // Type filter
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            
            // Currency filter
            if (currency != null && !currency.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("currency"), currency));
            }
            
            // Maturity date range filters
            if (maturityFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("maturityDate"), maturityFrom));
            }
            if (maturityTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("maturityDate"), maturityTo));
            }
            
            // Search filter - only add if search is not null/empty
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                
                Predicate namePredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")), 
                    criteriaBuilder.literal(searchPattern)
                );
                
                Predicate symbolPredicate = criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("symbol")), 
                    criteriaBuilder.literal(searchPattern)
                );
                
                // ISIN can be null, so we need to handle that
                Predicate isinPredicate = criteriaBuilder.and(
                    criteriaBuilder.isNotNull(root.get("isin")),
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("isin")), 
                        criteriaBuilder.literal(searchPattern)
                    )
                );
                
                // Combine search predicates with OR
                predicates.add(criteriaBuilder.or(namePredicate, symbolPredicate, isinPredicate));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
