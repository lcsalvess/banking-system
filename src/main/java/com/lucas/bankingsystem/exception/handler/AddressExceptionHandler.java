package com.lucas.bankingsystem.exception.handler;

import com.lucas.bankingsystem.controller.ClientController;
import com.lucas.bankingsystem.dto.response.exception.ErrorResponse;
import com.lucas.bankingsystem.integration.address.AddressLookupController;
import com.lucas.bankingsystem.integration.address.exception.AddressProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        AddressLookupController.class,
        ClientController.class
})
public class AddressExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(AddressExceptionHandler.class);

    @ExceptionHandler(AddressProviderUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleAddressProviderUnavailable() {
        log.error("Address provider unavailable");

        return new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "O serviço de consulta de endereços está temporariamente indisponível."
        );
    }
}
