package io.github.mubeenpathan19.seatreservation.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    private final String adminToken;
    public BearerTokenFilter(org.springframework.core.env.Environment env) {
        this.adminToken = env.getProperty("reservation.admin-token", "admin");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String requestId=request.getHeader("X-Request-Id");
        if(requestId==null||requestId.isBlank()) requestId=UUID.randomUUID().toString();
        MDC.put("requestId",requestId);
        response.setHeader("X-Request-Id",requestId);

        String auth=request.getHeader("Authorization");
        if(auth!=null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token=auth.substring(7).trim();
            if(!token.isBlank()){
                String userId=token;
                String role=userId.equals(adminToken) ? "ROLE_ADMIN" : "ROLE_USER";
                var authentication=new UsernamePasswordAuthenticationToken(
                    userId,null,List.of(new SimpleGrantedAuthority(role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put("userId",userId);
            }
        }
        try { chain.doFilter(request,response); }
        finally {
            MDC.remove("requestId"); MDC.remove("userId");
            SecurityContextHolder.clearContext();
        }
    }
}
