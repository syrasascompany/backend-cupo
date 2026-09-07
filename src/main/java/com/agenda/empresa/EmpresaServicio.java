package com.agenda.empresa;

import com.agenda.common.ReglaNegocioException;
import com.agenda.empresa.EmpresaDtos.*;
import com.agenda.notificaciones.CorreoServicio;
import com.agenda.usuario.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.agenda.catalogo.ProfesionalRepositorio;

/** Lo que usa TU panel de superadministrador. */
@Service
@RequiredArgsConstructor
public class EmpresaServicio {

    private final EmpresaRepositorio empresas;
    private final UsuarioRepositorio usuarios;
    private final ProfesionalRepositorio profesionales;
    private final PasswordEncoder codificador;
    private final CorreoServicio correo;

    /** Da de alta al cliente y crea de una vez el usuario del dueño. */
    @Transactional
    public EmpresaVista crear(CrearEmpresa datos) {
        if (empresas.existsBySlug(datos.slug()))
            throw new ReglaNegocioException("Ya existe una empresa con ese identificador");
        if (usuarios.existsByEmailIgnoreCase(datos.emailAdmin()))
            throw new ReglaNegocioException("Ya existe un usuario con ese correo");

        Empresa e = new Empresa();
        e.setNombre(datos.nombre());
        e.setSlug(datos.slug());
        e.setTelefonoWa(datos.telefonoWa());
        e.setPlan(datos.plan());
        e.setMaxProfesionales(datos.maxProfesionales());
        empresas.save(e);

        Usuario admin = new Usuario();
        admin.setEmpresaId(e.getId());
        admin.setEmail(datos.emailAdmin());
        admin.setNombre(datos.nombreAdmin());
        admin.setClaveHash(codificador.encode(datos.claveAdmin()));
        admin.setRol(Rol.ADMIN);
        usuarios.save(admin);

        // El dueño recibe sus datos de entrada apenas se crea la empresa
        correo.enviar(datos.emailAdmin(),
                "Su agenda de " + e.getNombre() + " ya está lista", """
                Hola %s,

                Ya puede entrar a la agenda de %s:

                Usuario: %s
                Contraseña: la que acordamos

                Al entrar, cambie la contraseña desde su perfil.

                Cualquier cosa, respóndanos por aquí.
                """.formatted(datos.nombreAdmin(), e.getNombre(), datos.emailAdmin()));

        return aVista(e);
    }

    public List<EmpresaVista> listar() {
        return empresas.findAll().stream().map(this::aVista).toList();
    }

    @Transactional
    public EmpresaVista cambiarPlan(Long empresaId, String plan, Integer maxProfesionales) {
        Empresa e = empresas.findById(empresaId)
                .orElseThrow(() -> new ReglaNegocioException("Empresa no encontrada"));
        e.setPlan(plan);
        e.setMaxProfesionales(maxProfesionales);
        return aVista(e);
    }

    @Transactional
    public void activar(Long empresaId, boolean activa) {
        Empresa e = empresas.findById(empresaId)
                .orElseThrow(() -> new ReglaNegocioException("Empresa no encontrada"));
        e.setActiva(activa);
    }

    /** Se llama antes de crear una profesional: hace respetar el plan contratado. */
    public void verificarCupoDeProfesionales(Long empresaId) {
        Empresa e = empresas.findById(empresaId)
                .orElseThrow(() -> new ReglaNegocioException("Empresa no encontrada"));
        long usados = profesionales.countByEmpresaIdAndActivoTrue(empresaId);
        if (usados >= e.getMaxProfesionales()) {
            throw new ReglaNegocioException(
                    "El plan actual permite " + e.getMaxProfesionales() + " profesionales");
        }
    }

    private EmpresaVista aVista(Empresa e) {
        return new EmpresaVista(e.getId(), e.getNombre(), e.getSlug(), e.getTelefonoWa(),
                e.getPlan(), e.getMaxProfesionales(), e.getActiva(),
                profesionales.countByEmpresaIdAndActivoTrue(e.getId()));
    }
}
