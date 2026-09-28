package cl.pedidos360.cupones.cupon;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Campaña de cupón: código canjeable N veces hasta su vencimiento. */
@Entity
@Table(name = "cupones")
public class Cupon {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String codigo;

    /** Porcentaje de descuento 1-100. */
    @Column(nullable = false)
    private int descuentoPct;

    @Column(nullable = false)
    private int usosMaximos;

    @Column(nullable = false)
    private int usosActuales = 0;

    @Column(nullable = false)
    private boolean activo = true;

    /** Null = sin vencimiento. */
    private Instant expiraEn;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Cupon() {
    }

    public Cupon(String codigo, int descuentoPct, int usosMaximos, Instant expiraEn) {
        this.id = UUID.randomUUID();
        this.codigo = codigo.toUpperCase();
        this.descuentoPct = descuentoPct;
        this.usosMaximos = usosMaximos;
        this.expiraEn = expiraEn;
        this.createdAt = Instant.now();
    }

    public boolean agotado() {
        return usosActuales >= usosMaximos;
    }

    public boolean vencido(Instant ahora) {
        return expiraEn != null && !expiraEn.isAfter(ahora);
    }

    /** Nulo si es canjeable, motivo en caso contrario. */
    public String motivoNoCanjeable(Instant ahora) {
        if (!activo) return "desactivado";
        if (vencido(ahora)) return "vencido";
        if (agotado()) return "agotado";
        return null;
    }

    public void registrarCanje() {
        this.usosActuales++;
    }

    public void desactivar() {
        this.activo = false;
    }

    public UUID getId() { return id; }
    public String getCodigo() { return codigo; }
    public int getDescuentoPct() { return descuentoPct; }
    public int getUsosMaximos() { return usosMaximos; }
    public int getUsosActuales() { return usosActuales; }
    public boolean isActivo() { return activo; }
    public Instant getExpiraEn() { return expiraEn; }
    public Instant getCreatedAt() { return createdAt; }
}
