package com.agenda.usuario;

import com.agenda.catalogo.CatalogoServicio;
import com.agenda.common.ContextoEmpresa;
import com.agenda.common.ReglaNegocioException;
import com.agenda.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioControlador {

    private final UsuarioRepositorio usuarios;
    private final ClaveServicio claves;
    private final PasswordEncoder codificador;
    private final CatalogoServicio catalogo;

    public record CambioClave(@NotBlank String actual,
                              @NotBlank @Size(min = 8) String nueva) {}

    @PostMapping("/mi-clave")
    public Map<String, String> cambiarMiClave(@Valid @RequestBody CambioClave datos,
                                              @AuthenticationPrincipal UsuarioAutenticado quien) {
        claves.cambiar(quien.id(), datos.actual(), datos.nueva());
        return Map.of("mensaje", "Contraseña cambiada.");
    }

    public record AccesoTrabajadora(@NotBlank String documento,
                                    @NotBlank @Size(min = 8) String clave) {}

    /**
     * Le crea el acceso a una manicurista. Entra con su cédula y solo ve
     * sus propias citas: no puede tocar horarios ni ver a las demás.
     */
    @PostMapping("/profesionales/{profesionalId}/acceso")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Map<String, String> crearAcceso(@PathVariable Long profesionalId,
                                           @Valid @RequestBody AccesoTrabajadora datos) {
        var profesional = catalogo.miProfesional(profesionalId);
        String documento = datos.documento().replaceAll("[^0-9]", "");

        Usuario usuario = usuarios.findByProfesionalId(profesionalId).orElseGet(Usuario::new);

        if (usuario.getId() == null) {
            if (usuarios.existsByDocumento(documento))
                throw new ReglaNegocioException("Esa cédula ya está registrada");
            usuario.setEmpresaId(ContextoEmpresa.actual());
            usuario.setProfesionalId(profesionalId);
            usuario.setRol(Rol.TRABAJADORA);
        }

        usuario.setNombre(profesional.getNombre());
        usuario.setDocumento(documento);
        usuario.setClaveHash(codificador.encode(datos.clave()));
        usuario.setActivo(true);
        usuarios.save(usuario);

        return Map.of("mensaje", profesional.getNombre() + " ya puede entrar con su cédula.");
    }

    /** Para saber si una manicurista ya tiene acceso creado. */
    @GetMapping("/profesionales/{profesionalId}/acceso")
    public Map<String, Object> verAcceso(@PathVariable Long profesionalId) {
        catalogo.miProfesional(profesionalId);
        return usuarios.findByProfesionalId(profesionalId)
                .map(u -> Map.<String, Object>of("tiene", true, "documento", u.getDocumento()))
                .orElse(Map.of("tiene", false));
    }
}
