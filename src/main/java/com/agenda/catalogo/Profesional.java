package com.agenda.catalogo;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity @Table(name = "profesional")
@Getter @Setter @NoArgsConstructor
public class Profesional {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(nullable = false) private String nombre;
    private String telefono;
    private String color;
    @Column(nullable = false) private Boolean activo = true;
    /**
     * Qué porcentaje de lo que produce se le paga a ella.
     * Casi siempre 50, pero no en todos los salones ni para todas.
     */
    @Column(name = "comision_pct", nullable = false)
    private BigDecimal comisionPct = new BigDecimal("50.00");
}
