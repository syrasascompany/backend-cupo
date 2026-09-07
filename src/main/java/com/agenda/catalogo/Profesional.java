package com.agenda.catalogo;

import jakarta.persistence.*;
import lombok.*;

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
}
