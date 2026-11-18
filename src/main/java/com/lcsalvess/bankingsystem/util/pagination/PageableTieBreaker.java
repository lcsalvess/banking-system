package com.lcsalvess.bankingsystem.util.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Guarantees a total ordering: without a unique trailing criterion, sorting by
 * columns with ties (role, active, balance, type, amount...) makes records
 * repeat or disappear between pages.
 */
public final class PageableTieBreaker {

  private static final String ID_PROPERTY = "id";

  private PageableTieBreaker() {
  }

  /**
   * Ensures pagination uses the entity ID as a unique sorting tie-breaker.
   *
   * @param pageable the pagination and sorting configuration
   * @return the pageable configuration with the ID tie-breaker appended
   */
  public static Pageable withIdTieBreaker(Pageable pageable) {
    return withTieBreaker(pageable, ID_PROPERTY);
  }

  /**
   * Ensures pagination uses the specified property as a sorting tie-breaker.
   *
   * <p>Appends the property in ascending order when it is not already present.
   * Unpaged requests and existing sorting criteria for the property remain unchanged.
   *
   * @param pageable the pagination and sorting configuration
   * @param property the property used as the unique sorting tie-breaker
   * @return the pageable configuration with the tie-breaker applied
   */
  public static Pageable withTieBreaker(Pageable pageable, String property) {
    if (pageable.isUnpaged()
        || pageable.getSort().getOrderFor(property) != null) {
      return pageable;
    }

    return PageRequest.of(
        pageable.getPageNumber(),
        pageable.getPageSize(),
        pageable.getSort().and(
            Sort.by(Sort.Direction.ASC, property)
        )
    );
  }
}
