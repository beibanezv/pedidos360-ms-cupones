package cl.pedidos360.cupones.messaging;

import cl.pedidos360.cupones.config.RabbitMQConfig;
import cl.pedidos360.cupones.cupon.Cupon;
import cl.pedidos360.cupones.cupon.CuponRepository;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Cuenta el canje cuando una orden registrada trae codigoCupon.
 * Órdenes sin cupón hacen ACK sin efectos. Código inexistente, vencido,
 * desactivado o agotado = error PERMANENTE (→ DLQ, ver BaseConsumer).
 * Falla de BD = TRANSITORIA (reintenta hasta MAX_REINTENTOS).
 *
 * SIMPLIFICADO: sin idempotencia por ordenId — si el mismo evento llega
 * dos veces, el canje se cuenta dos veces. Upgrade = tabla canjes(orden_id).
 */
@Component
public class CuponConsumer extends BaseConsumer {

  private static final Logger log = LoggerFactory.getLogger(CuponConsumer.class);

  private final CuponRepository repo;

  public CuponConsumer(CuponRepository repo) {
    this.repo = repo;
  }

  @Override
  protected int maxReintentos() {
    return RabbitMQConfig.MAX_REINTENTOS;
  }

  @RabbitListener(queues = RabbitMQConfig.CUPONES_QUEUE)
  public void onOrdenRegistrada(OrdenRegistradaEvent event, Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long tag, Message raw) throws IOException {
    consumir(channel, tag, raw, () -> {
      if (event.codigoCupon() == null || event.codigoCupon().isBlank()) {
        return;
      }
      String codigo = event.codigoCupon().toUpperCase();
      Cupon c = repo.findByCodigo(codigo)
          .orElseThrow(() -> new IllegalArgumentException("cupon no existe: " + codigo));
      String motivo = c.motivoNoCanjeable(Instant.now());
      if (motivo != null) {
        throw new IllegalArgumentException("cupon no canjeable (" + motivo + "): " + codigo);
      }
      c.registrarCanje();
      repo.save(c);
      log.info("canje contado codigo={} orden={} usos={}/{}",
          codigo, event.ordenId(), c.getUsosActuales(), c.getUsosMaximos());
    });
  }
}
