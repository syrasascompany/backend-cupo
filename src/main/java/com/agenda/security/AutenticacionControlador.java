package com.agenda.security;

import com.agenda.usuario.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AutenticacionControlador {

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final JwtServicio jwt;
    private final ClaveServicio claves;

    /**
     * "usuario" acepta correo o cédula: el dueño entra con su correo y las
     * manicuristas con su documento, que es lo que sí se saben de memoria.
     */
    public record Credenciales(@NotBlank String usuario, @NotBlank String clave) {}

    public record Sesion(String token, String nombre, String rol,
                         Long empresaId, Long profesionalId) {}

    @PostMapping("/login")
    public ResponseEntity<?> entrar(@Valid @RequestBody Credenciales cred) {
        String ingresado = cred.usuario().trim();

        Usuario usuario = ingresado.contains("@")
                ? usuarios.findByEmailIgnoreCase(ingresado).orElse(null)
                : usuarios.findByDocumento(ingresado.replaceAll("[^0-9]", "")).orElse(null);

        // Mismo mensaje siempre: no se le confirma a nadie qué cuentas existen.
        if (usuario == null
                || !usuario.getActivo()
                || !codificador.matches(cred.clave(), usuario.getClaveHash())) {
            return ResponseEntity.status(401).body(Map.of(
                    "mensaje", "Los datos no coinciden. Revise e intente de nuevo."));
        }

        return ResponseEntity.ok(new Sesion(
                jwt.generar(usuario), usuario.getNombre(), usuario.getRol().name(),
                usuario.getEmpresaId(), usuario.getProfesionalId()));
    }

    public record Correo(@NotBlank String email) {}

    /** Siempre responde igual, exista o no el correo. */
    @PostMapping("/olvide")
    public Map<String, String> olvide(@Valid @RequestBody Correo datos) {
        claves.solicitar(datos.email().trim());
        return Map.of("mensaje",
                "Si ese correo está registrado, le enviamos un enlace para restablecer la contraseña.");
    }

    public record Restablecer(@NotBlank String token, @NotBlank String clave) {}

    @PostMapping("/restablecer")
    public Map<String, String> restablecer(@Valid @RequestBody Restablecer datos) {
        claves.restablecer(datos.token(), datos.clave());
        return Map.of("mensaje", "Contraseña actualizada. Ya puede entrar.");
    }
}
