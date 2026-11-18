package com.lcsalvess.bankingsystem.util.pagination;

import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.pagination.InvalidSortPropertyException;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Validates pagination sorting properties against an allowed property whitelist.
 *
 * <p>Prevents unsupported or unintended properties from being used in sorting
 * requests.
 */
public final class SortWhitelist {

  private SortWhitelist() {
  }

  /**
   * Validates that all requested sorting properties are allowed.
   *
   * @param pageable the pagination and sorting configuration
   * @param allowedProperties the set of permitted sorting properties
   * @throws InvalidSortPropertyException if any requested property is not allowed
   */
  public static void validate(Pageable pageable, Set<String> allowedProperties) {
    for (Sort.Order order : pageable.getSort()) {
      if (!allowedProperties.contains(order.getProperty())) {
        throw new InvalidSortPropertyException(
            ApiErrorMessages.INVALID_SORT_PROPERTY
        );
      }
    }
  }
}
