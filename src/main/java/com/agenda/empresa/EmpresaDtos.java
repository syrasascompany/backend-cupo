package com.agenda.empresa;

import jakarta.validation.constraints.*;

public class EmpresaDtos {

    /** Lo que tú envías desde tu panel de superadministrador al dar de alta un cliente. */
    public record CrearEmpresa(
            @NotBlank String nombre,
            @NotBlank @Pattern(regexp = "[a-z0-9-]{3,80}", message = "solo minúsculas, números y guiones")
            String slug,
            String telefonoWa,
            @NotBlank String plan,
            @Min(1) @Max(100) Integer maxProfesionales,
            @NotBlank String emailAdmin,
            @NotBlank String nombreAdmin,
            @NotBlank @Size(min = 8, message = "mínimo 8 caracteres") String claveAdmin
    ) {}

    public record EmpresaVista(
            Long id, String nombre, String slug, String telefonoWa,
            String plan, Integer maxProfesionales, Boolean activa,
            long profesionalesUsados
    ) {}
}
