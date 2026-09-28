package cl.pedidos360.cupones.cupon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

/** Desactiva campañas vencidas. Avisa por log (SIMPLIFICADO: upgrade = publicar evento al topic). */
@Component
public class VencimientoScheduler {

    private static final Logger log = LoggerFactory.getLogger(VencimientoScheduler.class);

    private final CuponRepository repo;

    public VencimientoScheduler(CuponRepository repo) {
        this.repo = repo;
    }

    @Scheduled(fixedDelayString = "${app.cupones.revision-ms:60000}")
    @Transactional
    public void desactivarVencidos() {
        Instant ahora = Instant.now();
        List<Cupon> candidatos = repo.findByActivoTrueAndExpiraEnNotNull();
        for (Cupon c : candidatos) {
            if (c.vencido(ahora)) {
                c.desactivar();
                log.info("campaña vencida desactivada codigo={}", c.getCodigo());
            }
        }
    }
}
