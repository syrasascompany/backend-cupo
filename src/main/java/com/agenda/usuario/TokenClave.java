package com.agenda.usuario;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Enlace de un solo uso para restablecer la contraseña. */
@Entity @Table(name = "token_clave")
@Getter @Setter @NoArgsConstructor
public class TokenClave {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(nullable = false, unique = true) private String token;
    @Column(name = "vence_en", nullable = false) private Instant venceEn;
    @Column(nullable = false) private Boolean usado = false;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    public boolean sirve() {
        return !usado && venceEn.isAfter(Instant.now());
    }
}
