package com.lcsalvess.bankingsystem.exception.handler;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import com.lcsalvess.bankingsystem.exception.base.BusinessException;
import com.lcsalvess.bankingsystem.exception.database.DatabaseConstraint;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import com.lcsalvess.bankingsystem.exception.security.RateLimitExceededException;
import com.lcsalvess.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.hibernate.JDBCException;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Handles application exceptions and converts them into standardized API error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  // -------------- AUTHENTICATION ---------

  /**
   * Handles authentication failures caused by invalid credentials.
   *
   * @return the standardized unauthorized error response
   */
  @ExceptionHandler(BadCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiErrorResponse handleBadCredentials() {
    log.warn("Authentication failed: invalid credentials.");

    return ApiErrorResponse.of(
        clock,
        HttpStatus.UNAUTHORIZED.value(),
        ApiErrorMessages.INVALID_CREDENTIALS
    );
  }

  /**
   * Handles internal authentication processing failures, such as database
   * outages during login, which Spring Security may wrap in an
   * InternalAuthenticationServiceException.
   *
   * <p>These failures are not credential errors and must not be reported as HTTP 401.
   *
   * @param exception the authentication service exception
   * @return the standardized error response with the appropriate HTTP status
   */
  @ExceptionHandler(AuthenticationServiceException.class)
  public ResponseEntity<ApiErrorResponse> handleAuthenticationServiceException(
      AuthenticationServiceException exception) {

    if (isDatabaseFailure(exception)) {
      log.error("Authentication unavailable: database failure.", exception);

      return ResponseEntity
          .status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(ApiErrorResponse.of(
              clock,
              HttpStatus.SERVICE_UNAVAILABLE.value(),
              ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE
          ));
    }

    log.error("Authentication failed due to an internal error.", exception);

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of(
            clock,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ApiErrorMessages.INTERNAL_SERVER_ERROR
        ));
  }

  /**
   * Handles authentication failures that cannot be classified as invalid credentials.
   *
   * @param exception the authentication exception
   * @return the standardized unauthorized error response
   */
  @ExceptionHandler(AuthenticationException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiErrorResponse handleAuthenticationException(
      AuthenticationException exception) {

    log.warn(
        "Authentication failed: exceptionType={}",
        exception.getClass().getSimpleName()
    );

    return ApiErrorResponse.of(
        clock,
        HttpStatus.UNAUTHORIZED.value(),
        ApiErrorMessages.AUTHENTICATION_FAILED
    );
  }

  /**
   * Handles requests rejected because the configured rate limit was exceeded.
   *
   * <p>Includes the retry interval in the {@code Retry-After} response header.
   *
   * @param exception the rate limit exception containing the retry interval
   * @return the standardized too-many-requests error response
   */
  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
      RateLimitExceededException exception
  ) {
    // The operation, scope, and subject are already recorded by RateLimitProtectionService.
    return ResponseEntity
        .status(HttpStatus.TOO_MANY_REQUESTS)
        .header(
            HttpHeaders.RETRY_AFTER,
            Long.toString(exception.getRetryAfterSeconds())
        )
        .body(ApiErrorResponse.of(
            clock,
            HttpStatus.TOO_MANY_REQUESTS.value(),
            exception.getMessage()
        ));
  }

  // -------------- AUTHORIZATION ---------

  /**
   * Handles authorization failures when the authenticated user lacks the required permissions.
   *
   * @return the standardized forbidden error response
   */
  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiErrorResponse handleAuthorizationDenied() {
    log.warn("Access denied: user is not authorized to perform the operation.");

    return ApiErrorResponse.of(
        clock,
        HttpStatus.FORBIDDEN.value(),
        ApiErrorMessages.ACCESS_DENIED_RESOURCE
    );
  }

  // -------------- BUSINESS RULES ---------

  /**
   * Handles application-specific business exceptions.
   *
   * <p>Preserves the HTTP status and error message defined by the exception.
   *
   * @param exception the business exception containing the response details
   * @return the standardized error response
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiErrorResponse> handleBusinessException(
      BusinessException exception) {

    ApiErrorResponse error = ApiErrorResponse.of(
        clock,
        exception.getStatus().value(),
        exception.getMessage()
    );

    log.warn(
        "Business exception: status={}, exceptionType={}",
        exception.getStatus().value(),
        exception.getClass().getSimpleName()
    );

    return ResponseEntity
        .status(exception.getStatus())
        .body(error);
  }

  // -------------- DATA INTEGRITY ---------

  /**
   * Handles database integrity violations and translates known constraints into business errors.
   *
   * <p>Returns HTTP 409 for recognized constraint violations and HTTP 500
   * for unexpected integrity failures.
   *
   * @param exception the database integrity violation
   * @return the standardized error response
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {

    for (Map.Entry<DatabaseConstraint, String> entry
        : CONSTRAINT_ERROR_MESSAGES.entrySet()) {

      if (entry.getKey().isViolatedBy(exception)) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiErrorResponse.of(
                clock,
                HttpStatus.CONFLICT.value(),
                entry.getValue()
            ));
      }
    }

    // Do not log the exception: PostgreSQL's Detail field contains row values
    // (balances, CPF, and password hashes). Log only the SQLState and constraint name.
    log.warn(
        "Data integrity violation: sqlState={}, constraint={}",
        sqlStateOf(exception),
        constraintNameOf(exception)
    );

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of(
            clock,
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ApiErrorMessages.DATABASE_INTEGRITY_ERROR
        ));
  }

  // -------------- VALIDATION / REQUEST ---------

  /**
   * Handles validation failures raised by Jakarta Bean Validation.
   *
   * <p>Groups validation messages by property and prioritizes required-field
   * violations over other validation errors.
   *
   * @param exception the constraint violation exception
   * @return the standardized bad-request response containing field errors
   */
  @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiErrorResponse handleConstraintViolation(
      jakarta.validation.ConstraintViolationException exception) {

    Map<String, String> errors = new LinkedHashMap<>();
    Set<String> requiredFields = new HashSet<>();

    exception.getConstraintViolations().forEach(violation -> {
      String propertyPath = violation.getPropertyPath().toString();

      String field = propertyPath.substring(
          propertyPath.lastIndexOf('.') + 1
      );

      String code = violation.getConstraintDescriptor()
          .getAnnotation()
          .annotationType()
          .getSimpleName();

      String message = violation.getMessage();

      if (isRequiredValidation(code)) {
        errors.put(field, message);
        requiredFields.add(field);
      } else if (!requiredFields.contains(field)) {
        addValidationError(errors, field, message);
      }
    });

    return ApiErrorResponse.of(
        clock,
        HttpStatus.BAD_REQUEST.value(),
        ApiErrorMessages.VALIDATION_ERROR,
        errors
    );
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
      @NonNull HttpMessageNotReadableException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    return buildErrorResponse(
        ex,
        headers,
        status,
        request,
        ApiErrorMessages.INVALID_REQUEST_DATA
    );
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request
  ) {
    Map<String, String> errors = new LinkedHashMap<>();

    Set<String> requiredFields = new HashSet<>();

    ex.getBindingResult().getFieldErrors().forEach(error -> {
      String field = error.getField();
      String message = error.getDefaultMessage() != null
          ? error.getDefaultMessage()
          : "Valor inválido";

      if (isRequiredValidation(error.getCode())) {
        errors.put(field, message);
        requiredFields.add(field);
      } else if (!requiredFields.contains(field)) {
        addValidationError(errors, field, message);
      }
    });

    for (var error : ex.getBindingResult().getGlobalErrors()) {
      String message = error.getDefaultMessage();

      if (message == null) {
        message = "Objeto inválido";
      }

      addValidationError(errors, error.getObjectName(), message);
    }

    ApiErrorResponse body = ApiErrorResponse.of(
        clock,
        status.value(),
        ApiErrorMessages.VALIDATION_ERROR,
        errors
    );

    return handleExceptionInternal(
        ex,
        body,
        headers,
        status,
        request
    );
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMissingServletRequestParameter(
      @NonNull MissingServletRequestParameterException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    return buildErrorResponse(
        ex,
        headers,
        status,
        request,
        ApiErrorMessages.MISSING_REQUIRED_PARAMETER
    );
  }

  /**
   * Handles missing required headers and other servlet request binding failures.
   *
   * <p>Ensures that missing headers, such as Idempotency-Key, receive
   * the standardized API error response instead of Spring's default ProblemDetail response.
   *
   * @param ex the servlet request binding exception
   * @param headers the response headers
   * @param status the HTTP response status
   * @param request the current web request
   * @return the standardized error response, or null if no response is produced
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleServletRequestBindingException(
      @NonNull ServletRequestBindingException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    String message = ex instanceof MissingRequestHeaderException
        ? ApiErrorMessages.MISSING_REQUIRED_PARAMETER
        : ApiErrorMessages.INVALID_REQUEST_PARAMETER;

    return buildErrorResponse(
        ex,
        headers,
        status,
        request,
        message
    );
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleTypeMismatch(
      @NonNull TypeMismatchException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    return buildErrorResponse(
        ex,
        headers,
        status,
        request,
        ApiErrorMessages.INVALID_REQUEST_PARAMETER
    );
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleNoResourceFoundException(
      @NonNull NoResourceFoundException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    return buildErrorResponse(
        ex,
        headers,
        status,
        request,
        ApiErrorMessages.RESOURCE_NOT_FOUND
    );
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      @NonNull HttpRequestMethodNotSupportedException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    ApiErrorResponse error = ApiErrorResponse.of(
        clock,
        status.value(),
        ApiErrorMessages.METHOD_NOT_ALLOWED
    );

    return handleExceptionInternal(
        ex, error, headers, status, request
    );
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      @NonNull HttpMediaTypeNotSupportedException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {

    return buildErrorResponse(
        ex,
        headers,
        status,
        request,
        ApiErrorMessages.UNSUPPORTED_MEDIA_TYPE
    );
  }

  // -------------- INFRASTRUCTURE ---------

  /**
   * Handles failures caused by the unavailability of the external address provider.
   *
   * @param ex the address provider availability exception
   * @return the standardized service-unavailable error response
   */
  @ExceptionHandler(AddressProviderUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handleAddressProviderUnavailable(
      AddressProviderUnavailableException ex) {

    log.error("Address lookup provider unavailable.", ex);

    return ApiErrorResponse.of(
        clock,
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        ApiErrorMessages.ADDRESS_PROVIDER_UNAVAILABLE
    );
  }

  /**
   * Handles database connectivity and resource availability failures.
   *
   * @param exception the exception describing the database failure
   * @return the standardized service-unavailable error response
   */
  @ExceptionHandler({
      DataAccessResourceFailureException.class,
      QueryTimeoutException.class,
      CannotCreateTransactionException.class
  })
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handleDatabaseUnavailable(Exception exception) {

    log.error("Database unavailable.", exception);

    return ApiErrorResponse.of(
        clock,
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE
    );
  }

  /**
   * Handles failures when acquiring pessimistic database locks.
   *
   * @param exception the exception describing the locking failure
   * @return the standardized service-unavailable error response
   */
  @ExceptionHandler(PessimisticLockingFailureException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiErrorResponse handlePessimisticLockingFailure(
      PessimisticLockingFailureException exception) {

    log.warn("Could not acquire database lock.", exception);

    return ApiErrorResponse.of(
        clock,
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        ApiErrorMessages.DATABASE_TEMPORARILY_UNAVAILABLE
    );
  }

  // -------------- UNEXPECTED EXCEPTIONS ---------

  /**
   * Handles unexpected exceptions not covered by more specific exception handlers.
   *
   * @param exception the unexpected exception
   * @return the standardized internal-server-error response
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiErrorResponse handleUnexpectedException(Exception exception) {

    log.error("Unexpected error while processing request.", exception);

    return ApiErrorResponse.of(
        clock,
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        ApiErrorMessages.INTERNAL_SERVER_ERROR
    );
  }

  // -------------- HELPER METHODS ---------

  private @Nullable ResponseEntity<Object> buildErrorResponse(
      Exception ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request,
      String message) {

    ApiErrorResponse error = ApiErrorResponse.of(
        clock,
        status.value(),
        message
    );

    return handleExceptionInternal(
        ex,
        error,
        headers,
        status,
        request
    );
  }

  private static final Map<DatabaseConstraint, String> CONSTRAINT_ERROR_MESSAGES = Map.of(
      DatabaseConstraint.CLIENT_EMAIL_UNIQUE,
      ApiErrorMessages.CLIENT_EMAIL_ALREADY_EXISTS,

      DatabaseConstraint.CLIENT_CPF_UNIQUE,
      ApiErrorMessages.CLIENT_CPF_ALREADY_EXISTS,

      DatabaseConstraint.ACCOUNT_CLIENT_TYPE_ACTIVE_UNIQUE,
      ApiErrorMessages.ACTIVE_ACCOUNT_ALREADY_EXISTS,

      DatabaseConstraint.ACCOUNT_BALANCE_MAX,
      ApiErrorMessages.BALANCE_LIMIT_EXCEEDED,

      DatabaseConstraint.USER_USERNAME_UNIQUE,
      ApiErrorMessages.USERNAME_ALREADY_EXISTS,

      DatabaseConstraint.USER_EMAIL_UNIQUE,
      ApiErrorMessages.USER_EMAIL_ALREADY_EXISTS,

      DatabaseConstraint.SYSTEM_MIGRATION_USER_INACTIVE,
      ApiErrorMessages.SYSTEM_USER_CANNOT_BE_REACTIVATED,

      DatabaseConstraint.USER_USERNAME_RESERVED,
      ApiErrorMessages.RESERVED_USERNAME
  );

  private void addValidationError(
      Map<String, String> errors,
      String field,
      String message
  ) {
    errors.merge(field, message, (existing, next) -> existing + "; " + next);
  }

  private static boolean isDatabaseFailure(Throwable throwable) {
    for (Throwable cause = throwable;
        cause != null;
        cause = cause.getCause()) {

      if (cause instanceof DataAccessException
          || cause instanceof TransactionException) {
        return true;
      }
    }

    return false;
  }

  private static String sqlStateOf(Throwable throwable) {
    for (Throwable cause = throwable;
        cause != null;
        cause = cause.getCause()) {

      if (cause instanceof JDBCException jdbcException) {
        java.sql.SQLException sqlException =
            jdbcException.getSQLException();

        if (sqlException != null
            && sqlException.getSQLState() != null) {
          return sqlException.getSQLState();
        }
      }

      if (cause instanceof SQLException sqlException
          && sqlException.getSQLState() != null) {
        return sqlException.getSQLState();
      }
    }

    return "unknown";
  }

  private static String constraintNameOf(Throwable throwable) {
    for (Throwable cause = throwable;
        cause != null;
        cause = cause.getCause()) {

      if (cause instanceof ConstraintViolationException violation
          && violation.getConstraintName() != null) {
        return violation.getConstraintName();
      }

      if (cause instanceof PSQLException postgresException) {
        var serverError = postgresException.getServerErrorMessage();

        if (serverError != null
            && serverError.getConstraint() != null) {
          return serverError.getConstraint();
        }
      }
    }

    return "unknown";
  }

  private boolean isRequiredValidation(String code) {
    return Set.of("NotBlank", "NotNull", "NotEmpty").contains(code);
  }
}
