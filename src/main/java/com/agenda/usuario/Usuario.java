package com.agenda.usuario;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "usuario")
@Getter @Setter @NoArgsConstructor
public class Usuario {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id") private Long empresaId;   // nulo para SUPERADMIN

    @Column(unique = true) private String email;

    /** Cédula. Es como entran las trabajadoras. */
    @Column(unique = true) private String documento;
    @Column(name = "clave_hash", nullable = false) private String claveHash;
    @Column(nullable = false) private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private Rol rol;

    /** Solo para TRABAJADORA: a qué profesional corresponde este usuario. */
    @Column(name = "profesional_id") private Long profesionalId;

    @Column(nullable = false) private Boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();
}
