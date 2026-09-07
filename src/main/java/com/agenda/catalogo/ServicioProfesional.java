package com.agenda.catalogo;

import jakarta.persistence.*;
import lombok.*;

/**
 * Cuánto se demora ESTA profesional en ESTE servicio.
 * Es la pieza que evita los cruces: dos manicuristas no tardan
 * lo mismo en unas acrílicas.
 */
@Entity @Table(name = "servicio_profesional")
@Getter @Setter @NoArgsConstructor
public class ServicioProfesional {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "servicio_id", nullable = false) private Long servicioId;
    @Column(name = "profesional_id", nullable = false) private Long profesionalId;
    /**
     * Ajuste para esta persona. En nulo significa que se demora
     * lo normal del servicio.
     */
    @Column(name = "duracion_min") private Integer duracionMin;
}
