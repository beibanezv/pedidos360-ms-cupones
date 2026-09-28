package cl.pedidos360.cupones.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError e : ex.getBindingResult().getFieldErrors()) {
            campos.put(e.getField(), e.getDefaultMessage());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("error", "validacion");
        r.put("campos", campos);
        return r;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> estado(ResponseStatusException ex) {
        // 404 y 409 llegan aquí; se responde el código real de la excepción.
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("error", ex.getStatusCode().value() == 409 ? "conflicto" : "no_encontrado");
        r.put("mensaje", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(r);
    }
}
