package cl.pedidos360.cupones.cupon;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CrearCuponRequest(
        @NotBlank(message = "codigo es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "codigo solo admite letras, números, guion y guion bajo")
        String codigo,

        @Min(value = 1, message = "descuentoPct mínimo 1")
        @Max(value = 100, message = "descuentoPct máximo 100")
        int descuentoPct,

        @Min(value = 1, message = "usosMaximos mínimo 1")
        int usosMaximos,

        /** Null = sin vencimiento. */
        @Future(message = "expiraEn debe ser futuro")
        Instant expiraEn
) {
}
