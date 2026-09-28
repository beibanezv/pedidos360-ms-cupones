package cl.pedidos360.cupones.cupon;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CuponRepository extends JpaRepository<Cupon, UUID> {
    Optional<Cupon> findByCodigo(String codigo);

    List<Cupon> findByActivoTrueAndExpiraEnNotNull();
}
