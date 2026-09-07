package com.agenda.usuario;

import com.agenda.common.NoEncontradoException;
import com.agenda.common.ReglaNegocioException;
import com.agenda.notificaciones.CorreoServicio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClaveServicio {

    private final UsuarioRepositorio usuarios;
    private final TokenClaveRepositorio tokens;
    private final PasswordEncoder codificador;
    private final CorreoServicio correo;

    private final SecureRandom aleatorio = new SecureRandom();

    @Value("${app.url-panel:http://localhost:4200}")
    private String urlPanel;

    /**
     * Manda el enlace de recuperación.
     *
     * No devuelve nada distinto si el correo existe o no: si respondiera
     * diferente, cualquiera podría averiguar qué correos están registrados.
     */
    @Transactional
    public void solicitar(String email) {
        usuarios.findByEmailIgnoreCase(email).ifPresent(usuario -> {
            if (!usuario.getActivo()) return;

            byte[] bytes = new byte[32];
            aleatorio.nextBytes(bytes);
            String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            TokenClave token = new TokenClave();
            token.setUsuarioId(usuario.getId());
            token.setToken(valor);
            token.setVenceEn(Instant.now().plus(Duration.ofHours(2)));
            tokens.save(token);

            correo.enviar(usuario.getEmail(), "Restablecer su contraseña de Cupo", """
                    Hola %s,

                    Para poner una contraseña nueva, entre aquí:
                    %s/recuperar?token=%s

                    El enlace vence en 2 horas y solo sirve una vez.
                    Si usted no lo pidió, ignore este correo.
                    """.formatted(usuario.getNombre(), urlPanel, valor));
        });
    }

    @Transactional
    public void restablecer(String valorToken, String claveNueva) {
        validarClave(claveNueva);

        TokenClave token = tokens.findByToken(valorToken)
                .orElseThrow(() -> new ReglaNegocioException("El enlace no es válido"));

        if (!token.sirve())
            throw new ReglaNegocioException("El enlace ya venció o ya fue usado");

        Usuario usuario = usuarios.findById(token.getUsuarioId())
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        usuario.setClaveHash(codificador.encode(claveNueva));
        token.setUsado(true);
    }

    /** Cambiar la propia contraseña estando dentro. */
    @Transactional
    public void cambiar(Long usuarioId, String claveActual, String claveNueva) {
        validarClave(claveNueva);

        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new NoEncontradoException("Usuario no encontrado"));

        if (!codificador.matches(claveActual, usuario.getClaveHash()))
            throw new ReglaNegocioException("La contraseña actual no es correcta");

        usuario.setClaveHash(codificador.encode(claveNueva));
    }

    private void validarClave(String clave) {
        if (clave == null || clave.length() < 8)
            throw new ReglaNegocioException("La contraseña debe tener al menos 8 caracteres");
    }
}
