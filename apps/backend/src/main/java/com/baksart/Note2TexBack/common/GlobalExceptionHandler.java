package com.baksart.Note2TexBack.common;

import org.springframework.http.*;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("error", "validation");
        var errs = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList();
        body.put("details", errs);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<Map<String,Object>> handleErr(ErrorResponseException e) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("error", e.getStatusCode().toString());
        body.put("message", e.getBody().getDetail());
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleAny(Exception e) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("error", "internal");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
