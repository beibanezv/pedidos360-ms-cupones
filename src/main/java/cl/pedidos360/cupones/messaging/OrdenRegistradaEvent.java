package cl.pedidos360.cupones.messaging;

import java.util.List;
import java.util.UUID;

/**
 * Espejo del evento que publica ms-orders. Solo viajan los campos que
 * este consumidor necesita: el codigoCupon (nullable, Jackson lo deja
 * en null si el productor aún no lo manda).
 */
public record OrdenRegistradaEvent(
    UUID ordenId,
    String usuarioId,
    List<Item> items,
    String codigoCupon) {

  public record Item(UUID productoId, int cantidad) {}
}
