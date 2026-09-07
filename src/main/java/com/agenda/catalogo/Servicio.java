package com.agenda.catalogo;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "servicio")
@Getter @Setter @NoArgsConstructor
public class Servicio {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false) private Long empresaId;
    @Column(nullable = false) private String nombre;

    /** En centavos para no arrastrar errores de redondeo. */
    @Column(name = "precio_centavos", nullable = false) private Long precioCentavos = 0L;

    /** Cuánto se demora normalmente. Cada profesional puede ajustarla. */
    @Column(name = "duracion_min", nullable = false) private Integer duracionMin = 60;

    @Column(nullable = false) private Boolean activo = true;
}
