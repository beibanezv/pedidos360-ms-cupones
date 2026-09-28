package cl.pedidos360.cupones.messaging;

import cl.pedidos360.cupones.cupon.Cupon;
import cl.pedidos360.cupones.cupon.CuponRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuponConsumerTest {

    @Mock
    CuponRepository repository;
    @Mock
    Channel channel;

    CuponConsumer consumer;

    @BeforeEach
    void setup() {
        consumer = new CuponConsumer(repository);
    }

    private OrdenRegistradaEvent eventoConCupon(String codigoCupon) {
        return new OrdenRegistradaEvent(UUID.randomUUID(), "user-1",
                List.of(new OrdenRegistradaEvent.Item(UUID.randomUUID(), 1)), codigoCupon);
    }

    private Message mensajeSinMuertes() {
        return new Message(new byte[0], new MessageProperties());
    }

    @Test
    void ordenConCuponValido_registraCanje_yAck() throws Exception {
        when(repository.findByCodigo("BIENVENIDA10"))
                .thenReturn(Optional.of(new Cupon("BIENVENIDA10", 10, 5, null)));

        consumer.onOrdenRegistrada(eventoConCupon("bienvenida10"), channel, 1L, mensajeSinMuertes());

        ArgumentCaptor<Cupon> captor = ArgumentCaptor.forClass(Cupon.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsosActuales()).isEqualTo(1);
        verify(channel).basicAck(1L, false);
    }

    @Test
    void ordenSinCupon_soloAck() throws Exception {
        consumer.onOrdenRegistrada(eventoConCupon(null), channel, 2L, mensajeSinMuertes());

        verify(repository, never()).save(any());
        verify(channel).basicAck(2L, false);
    }

    @Test
    void cuponInexistente_aDlq() throws Exception {
        when(repository.findByCodigo("FALSO")).thenReturn(Optional.empty());

        consumer.onOrdenRegistrada(eventoConCupon("FALSO"), channel, 3L, mensajeSinMuertes());

        verify(repository, never()).save(any());
        verify(channel).basicNack(3L, false, false);
    }

    @Test
    void cuponAgotado_aDlq() throws Exception {
        Cupon c = new Cupon("BIENVENIDA10", 10, 1, null);
        c.registrarCanje();
        when(repository.findByCodigo("BIENVENIDA10")).thenReturn(Optional.of(c));

        consumer.onOrdenRegistrada(eventoConCupon("BIENVENIDA10"), channel, 4L, mensajeSinMuertes());

        verify(repository, never()).save(any());
        verify(channel).basicNack(4L, false, false);
    }

    @Test
    void cuponVencido_aDlq() throws Exception {
        Cupon c = new Cupon("TARDE", 10, 5, Instant.now().minusSeconds(60));
        when(repository.findByCodigo("TARDE")).thenReturn(Optional.of(c));

        consumer.onOrdenRegistrada(eventoConCupon("TARDE"), channel, 5L, mensajeSinMuertes());

        verify(repository, never()).save(any());
        verify(channel).basicNack(5L, false, false);
    }

    @Test
    void falloTransitorio_reencola() throws Exception {
        when(repository.findByCodigo("BIENVENIDA10"))
                .thenThrow(new RuntimeException("BD caída"));

        consumer.onOrdenRegistrada(eventoConCupon("BIENVENIDA10"), channel, 6L, mensajeSinMuertes());

        verify(channel).basicNack(6L, false, true);
    }

    @Test
    void falloTransitorioAgotado_aDlq() throws Exception {
        when(repository.findByCodigo("BIENVENIDA10"))
                .thenThrow(new RuntimeException("BD caída"));

        MessageProperties props = new MessageProperties();
        props.setHeader("x-death", List.of(
                Map.of("count", 3L, "queue", "p360.cupones.queue", "reason", "rejected")));
        Message raw = new Message(new byte[0], props);

        consumer.onOrdenRegistrada(eventoConCupon("BIENVENIDA10"), channel, 7L, raw);

        verify(channel).basicNack(7L, false, false);
    }
}
