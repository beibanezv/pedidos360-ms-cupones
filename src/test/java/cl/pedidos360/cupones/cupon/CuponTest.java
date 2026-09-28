package cl.pedidos360.cupones.cupon;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CuponTest {

    @Test
    void constructor_normalizaCodigo_aMayusculas() {
        Cupon c = new Cupon("bienvenida10", 10, 5, null);
        assertThat(c.getCodigo()).isEqualTo("BIENVENIDA10");
        assertThat(c.getId()).isNotNull();
        assertThat(c.getUsosActuales()).isZero();
        assertThat(c.isActivo()).isTrue();
    }

    @Test
    void cuponNuevo_esCanjeable() {
        assertThat(new Cupon("BIENVENIDA10", 10, 5, null)
                .motivoNoCanjeable(Instant.now())).isNull();
    }

    @Test
    void registrarCanje_sumaUsos_yAlAgotar_quedaAgotado() {
        Cupon c = new Cupon("POCOS", 10, 2, null);
        c.registrarCanje();
        assertThat(c.getUsosActuales()).isEqualTo(1);
        assertThat(c.motivoNoCanjeable(Instant.now())).isNull();
        c.registrarCanje();
        assertThat(c.getUsosActuales()).isEqualTo(2);
        assertThat(c.motivoNoCanjeable(Instant.now())).isEqualTo("agotado");
    }

    @Test
    void cuponDesactivado_noEsCanjeable() {
        Cupon c = new Cupon("VIEJO", 10, 5, null);
        c.desactivar();
        assertThat(c.motivoNoCanjeable(Instant.now())).isEqualTo("desactivado");
    }

    @Test
    void cuponVencido_noEsCanjeable() {
        Cupon c = new Cupon("TARDE", 10, 5, Instant.now().minusSeconds(3600));
        assertThat(c.motivoNoCanjeable(Instant.now())).isEqualTo("vencido");
    }

    @Test
    void cuponConVencimientoFuturo_esCanjeable() {
        Cupon c = new Cupon("PRONTO", 10, 5, Instant.now().plusSeconds(3600));
        assertThat(c.motivoNoCanjeable(Instant.now())).isNull();
    }
}
