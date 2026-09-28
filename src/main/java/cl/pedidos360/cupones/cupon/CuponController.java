package cl.pedidos360.cupones.cupon;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/cupones")
public class CuponController {

    private final CuponRepository repo;

    public CuponController(CuponRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crear(@Valid @RequestBody CrearCuponRequest req) {
        if (repo.findByCodigo(req.codigo().toUpperCase()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "codigo ya existe");
        }
        Cupon c = repo.save(new Cupon(req.codigo(), req.descuentoPct(), req.usosMaximos(), req.expiraEn()));
        return ficha(c, null);
    }

    @GetMapping("/{codigo}")
    public Map<String, Object> ver(@PathVariable String codigo) {
        return ficha(buscar(codigo), null);
    }

    /** Lo que usaría el checkout para decidir si aplica el descuento. */
    @GetMapping("/{codigo}/validar")
    public Map<String, Object> validar(@PathVariable String codigo) {
        Cupon c = buscar(codigo);
        String motivo = c.motivoNoCanjeable(Instant.now());
        Map<String, Object> r = ficha(c, motivo);
        r.put("valido", motivo == null);
        return r;
    }

    private Cupon buscar(String codigo) {
        return repo.findByCodigo(codigo.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "cupon no existe"));
    }

    private static Map<String, Object> ficha(Cupon c, String motivo) {
        // LinkedHashMap (no Map.of): expiraEn y motivo pueden ser null.
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", c.getId());
        r.put("codigo", c.getCodigo());
        r.put("descuentoPct", c.getDescuentoPct());
        r.put("usosActuales", c.getUsosActuales());
        r.put("usosMaximos", c.getUsosMaximos());
        r.put("activo", c.isActivo());
        r.put("expiraEn", c.getExpiraEn());
        r.put("motivo", motivo);
        return r;
    }
}
