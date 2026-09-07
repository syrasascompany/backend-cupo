package com.agenda.security;

import com.agenda.common.ContextoEmpresa;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FiltroJwt extends OncePerRequestFilter {

    private final JwtServicio jwt;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String cabecera = req.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            try {
                Claims c = jwt.leer(cabecera.substring(7));
                String rol = c.get("rol", String.class);

                var auth = new UsernamePasswordAuthenticationToken(
                        new UsuarioAutenticado(
                                c.get("uid", Number.class).longValue(),
                                c.getSubject(),
                                rol,
                                c.get("empresaId") == null ? null : c.get("empresaId", Number.class).longValue(),
                                c.get("profesionalId") == null ? null : c.get("profesionalId", Number.class).longValue()),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol)));

                SecurityContextHolder.getContext().setAuthentication(auth);

                if (c.get("empresaId") != null) {
                    ContextoEmpresa.fijar(c.get("empresaId", Number.class).longValue());
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();  // token inválido o vencido
            }
        }

        try {
            chain.doFilter(req, res);
        } finally {
            ContextoEmpresa.limpiar();   // el hilo se reutiliza: hay que soltarlo siempre
        }
    }
}
