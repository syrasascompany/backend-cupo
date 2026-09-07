package com.agenda.empresa;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "empresa")
@Getter @Setter @NoArgsConstructor
public class Empresa {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String nombre;
    @Column(nullable = false, unique = true) private String slug;

    @Column(name = "telefono_wa") private String telefonoWa;

    @Column(name = "zona_horaria", nullable = false)
    private String zonaHoraria = "America/Bogota";

    @Column(nullable = false) private String plan = "ESENCIAL";

    @Column(name = "max_profesionales", nullable = false)
    private Integer maxProfesionales = 2;

    @Column(nullable = false) private Boolean activa = true;

    /** El identificador que Meta le da al número. Llega en cada webhook. */
    @Column(name = "wa_phone_number_id") private String waPhoneNumberId;

    /** Token permanente de la cuenta de WhatsApp de este negocio. */
    @Column(name = "wa_token") private String waToken;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn = Instant.now();
}
