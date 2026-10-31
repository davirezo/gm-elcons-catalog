package br.com.gmelcons.catalog.exception;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio, com o status HTTP que deve ser devolvido. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    public static BusinessException unprocessable(String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
