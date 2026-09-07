package com.agenda.common;

/**
 * Guarda la empresa del usuario autenticado durante la petición.
 * Todas las consultas filtran por aquí: es lo que impide que una
 * empresa vea los datos de otra.
 */
public final class ContextoEmpresa {

    private static final ThreadLocal<Long> ACTUAL = new ThreadLocal<>();

    private ContextoEmpresa() {}

    public static void fijar(Long empresaId) { ACTUAL.set(empresaId); }

    public static Long actual() {
        Long id = ACTUAL.get();
        if (id == null) throw new ReglaNegocioException("No hay empresa en el contexto de la petición");
        return id;
    }

    public static Long actualONulo() { return ACTUAL.get(); }

    public static void limpiar() { ACTUAL.remove(); }
}
