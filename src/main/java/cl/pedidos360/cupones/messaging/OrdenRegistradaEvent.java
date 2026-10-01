package cl.pedidos360.cupones.messaging;

import java.util.List;
import java.util.UUID;

/**
 * Espejo del evento que publica ms-orders. Solo viajan los campos que
 * este consumidor necesita: el codigoCupon (nullable, Jackson lo deja
 * en null si el productor aún no lo manda). emailComprador viaja para
 * ms-notificaciones; aquí se ignora (pero debe existir: Jackson es
 * estricto con propiedades desconocidas).
 */
public record OrdenRegistradaEvent(
    UUID ordenId,
    String usuarioId,
    String emailComprador,
    List<Item> items,
    String codigoCupon) {

  public record Item(UUID productoId, int cantidad) {}
}
