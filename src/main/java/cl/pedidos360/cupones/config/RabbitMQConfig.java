package cl.pedidos360.cupones.config;

import cl.pedidos360.cupones.messaging.OrdenRegistradaEvent;
import com.fasterxml.jackson.databind.JavaType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología RabbitMQ que usa ms-cupones (rúbrica EP3/EP4 indicadores 1 y 2).
 * Nombres centralizados aquí: ningún otro archivo del servicio repite literales.
 *
 * Rutas documentadas:
 *   ms-orders  --topic---> p360.eventos / orden.registrada
 *                              └─> p360.cupones.queue (la consume CuponConsumer:
 *                                  si el evento trae codigoCupon, cuenta el canje)
 *   Si un mensaje muere (rechazo/NACK sin requeue) va a p360.dlx
 *   con routing key = nombre de la cola origen, y de ahí a su DLQ.
 */
@Configuration
public class RabbitMQConfig {

  // Caso de uso "contar canje": consume orden.registrada del topic.
  public static final String EVENTOS_EXCHANGE = "p360.eventos";
  public static final String CUPONES_QUEUE = "p360.cupones.queue";
  public static final String ORDEN_REGISTRADA_ROUTING_KEY = "orden.registrada";

  // Límite de reintentos de errores transitorios (ver CuponConsumer).
  public static final int MAX_REINTENTOS = 3;

  // Dead-letter exchange compartido (direct: cada DLQ se enlaza con su propia key).
  public static final String DLX_EXCHANGE = "p360.dlx";
  public static final String CUPONES_DLQ = "p360.cupones.dlq";

  // Declarar lo que ya declaran otros servicios es idempotente en RabbitMQ
  // (mismos argumentos): así la cola existe aunque ms-orders aún no arrancó.
  @Bean
  TopicExchange eventosExchange() {
    return new TopicExchange(EVENTOS_EXCHANGE, true, false);
  }

  @Bean
  Queue cuponesQueue() {
    return QueueBuilder.durable(CUPONES_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", CUPONES_QUEUE)
        .build();
  }

  @Bean
  Binding cuponesBinding(Queue cuponesQueue, TopicExchange eventosExchange) {
    return new Binding(CUPONES_QUEUE, Binding.DestinationType.QUEUE,
        EVENTOS_EXCHANGE, ORDEN_REGISTRADA_ROUTING_KEY, null);
  }

  @Bean
  DirectExchange deadLetterExchange() {
    return new DirectExchange(DLX_EXCHANGE, true, false);
  }

  @Bean
  Queue cuponesDlq() {
    return QueueBuilder.durable(CUPONES_DLQ).build();
  }

  @Bean
  Binding cuponesDlqBinding(Queue cuponesDlq, DirectExchange deadLetterExchange) {
    return new Binding(CUPONES_DLQ, Binding.DestinationType.QUEUE,
        DLX_EXCHANGE, CUPONES_QUEUE, null);
  }

  /** __TypeId__ = nombre simple; alias FQCN historicos -> clase local. */
  public static DefaultJackson2JavaTypeMapper jsonTypeMapper() {
    Map<String, Class<?>> ids = new LinkedHashMap<>();
    ids.put("com.pedidos360.orders.messaging.OrdenRegistradaEvent", OrdenRegistradaEvent.class);
    ids.put("com.pedidos360.productos.messaging.OrdenRegistradaEvent", OrdenRegistradaEvent.class);
    ids.put("com.pedidos360.notificaciones.messaging.OrdenRegistradaEvent", OrdenRegistradaEvent.class);
    ids.put("OrdenRegistradaEvent", OrdenRegistradaEvent.class);

    DefaultJackson2JavaTypeMapper mapper = new DefaultJackson2JavaTypeMapper() {
      @Override
      public void fromJavaType(JavaType javaType, MessageProperties properties) {
        super.fromJavaType(javaType, properties);
        if (javaType.getRawClass() == OrdenRegistradaEvent.class) {
          properties.setHeader(DEFAULT_CLASSID_FIELD_NAME, "OrdenRegistradaEvent");
        }
      }
    };
    mapper.setIdClassMapping(ids);
    mapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.TYPE_ID);
    return mapper;
  }

  @Bean
  MessageConverter jsonMessageConverter() {
    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
    converter.setJavaTypeMapper(jsonTypeMapper());
    return converter;
  }
}
