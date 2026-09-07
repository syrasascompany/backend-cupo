package com.agenda.security;

public record UsuarioAutenticado(Long id, String email, String rol,
                                 Long empresaId, Long profesionalId) {

    public boolean esTrabajadora() { return "TRABAJADORA".equals(rol); }
    public boolean esSuperadmin()  { return "SUPERADMIN".equals(rol); }
}
