package com.julio.lifeorganizer.transactions.service;

import com.julio.lifeorganizer.common.api.PageMeta;
import com.julio.lifeorganizer.common.exception.InvalidQueryException;
import com.julio.lifeorganizer.common.exception.NotFoundException;
import com.julio.lifeorganizer.config.PaginationProperties;
import com.julio.lifeorganizer.recurring.service.RecurringMaterialiser;
import com.julio.lifeorganizer.transactions.domain.CursorCodec;
import com.julio.lifeorganizer.transactions.persistence.TransactionEntity;
import com.julio.lifeorganizer.transactions.persistence.TransactionRepository;
import com.julio.lifeorganizer.transactions.web.dto.CreateTransactionRequest;
import com.julio.lifeorganizer.transactions.web.dto.TransactionResponse;
import com.julio.lifeorganizer.transactions.web.dto.UpdateTransactionRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Single source of business logic for transaction CRUD.
//
// Ownership invariant: every read/write embeds user_id from the JWT subject in the query
// predicate. The three "not found" cases (missing id, not owner, soft-deleted) all hit the
// same exception so the response body is byte-identical (R8 / AC-T18).
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final String NOT_FOUND_MESSAGE = "Transaction not found";
    private static final String NOT_FOUND_CODE = "TRANSACTION_NOT_FOUND";

    private final TransactionRepository repository;
    private final PaginationProperties paginationProperties;
    private final Clock clock;
    private final RecurringMaterialiser recurringMaterialiser;

    public TransactionService(TransactionRepository repository,
                              PaginationProperties paginationProperties,
                              Clock clock,
                              RecurringMaterialiser recurringMaterialiser) {
        this.repository = repository;
        this.paginationProperties = paginationProperties;
        this.clock = clock;
        this.recurringMaterialiser = recurringMaterialiser;
    }

    @Transactional
    public TransactionResponse create(Long userId, CreateTransactionRequest request) {
        TransactionEntity entity = TransactionEntity.createNew(
                userId,
                request.amount(),
                request.type(),
                request.category().trim(),
                cleanDescription(request.description()),
                request.transactionDate());
        return TransactionResponse.from(repository.save(entity));
    }

    // Not readOnly: the materialiser may INSERT new transactions on this call, so the
    // surrounding transaction must allow writes (the class default is readOnly = true).
    @Transactional
    public PageResult list(Long userId, ListQuery query) {
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new InvalidQueryException("from must be on or before to");
        }
        // Catch up any due recurring transactions before listing so the user always sees
        // an up-to-date view. Cheap when nothing is due (single indexed query).
        recurringMaterialiser.materialiseFor(userId);
        int limit = resolveLimit(query.limit());

        // Fetch limit+1 to detect "has more" without an extra count query.
        PageRequest pageable = PageRequest.of(0, limit + 1);
        List<TransactionEntity> rows;
        if (query.cursor() == null || query.cursor().isBlank()) {
            rows = repository.findFirstPage(userId, query.from(), query.to(), pageable);
        } else {
            CursorCodec.DecodedCursor decoded = CursorCodec.decode(query.cursor());
            rows = repository.findPageAfterCursor(
                    userId, query.from(), query.to(),
                    decoded.date(), decoded.id(), pageable);
        }

        boolean hasMore = rows.size() > limit;
        List<TransactionEntity> page = hasMore ? rows.subList(0, limit) : rows;
        List<TransactionResponse> data = page.stream()
                .map(TransactionResponse::from)
                .toList();
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            TransactionEntity last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.getTransactionDate(), last.getId());
        }
        return new PageResult(data, new PageMeta(nextCursor, limit));
    }

    public TransactionResponse findOne(Long userId, Long id) {
        return repository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE, NOT_FOUND_CODE));
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, UpdateTransactionRequest request) {
        TransactionEntity entity = repository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE, NOT_FOUND_CODE));
        entity.replaceWith(
                request.amount(),
                request.type(),
                request.category().trim(),
                cleanDescription(request.description()),
                request.transactionDate());
        return TransactionResponse.from(repository.save(entity));
    }

    // Description is optional on the API (Slice 5). The DB column stays NOT NULL,
    // so we coerce null / blank to "" here. Trimming is preserved for present values.
    private static String cleanDescription(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    @Transactional
    public void softDelete(Long userId, Long id) {
        TransactionEntity entity = repository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE, NOT_FOUND_CODE));
        entity.markDeleted(clock.instant());
        repository.save(entity);
    }

    private int resolveLimit(Integer requested) {
        int limit = requested == null ? paginationProperties.defaultLimit() : requested;
        if (limit < 1 || limit > paginationProperties.maxLimit()) {
            throw new InvalidQueryException(
                    "limit must be between 1 and " + paginationProperties.maxLimit());
        }
        return limit;
    }

    public record ListQuery(String cursor, Integer limit, LocalDate from, LocalDate to) {
    }

    public record PageResult(List<TransactionResponse> items, PageMeta meta) {
    }
}
