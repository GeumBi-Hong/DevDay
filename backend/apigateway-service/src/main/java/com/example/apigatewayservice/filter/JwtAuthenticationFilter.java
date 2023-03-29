package com.example.apigatewayservice.filter;

import com.example.apigatewayservice.auth.JWTUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private JWTUtil jwtUtil;

    @Autowired
    public JwtAuthenticationFilter(JWTUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    public static class Config {

    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 헤더에서 토큰 뽑아오기
            String accessToken = jwtUtil.resolveToken(request);

            // 유효한 토큰인지 확인합니다.
            jwtUtil.validateToken(accessToken);

            String userId = jwtUtil.getUserPk(accessToken);
            exchange.getRequest().mutate()
                    .headers(httpHeaders -> httpHeaders.add("userId", userId)).build();

            return chain.filter(exchange);
        };
    }

    @Bean
    public ErrorWebExceptionHandler tokenValidation() {
        return new JwtTokenExceptionHandler();
    }

    public class JwtTokenExceptionHandler implements ErrorWebExceptionHandler {

        private String getErrorCode(int errorCode) {
            return "{\"errorCode\":" + errorCode +"}";
        }

        @Override
        public Mono<Void> handle(
                ServerWebExchange exchange, Throwable ex) {
            int errorCode = 503;
            if (ex.getClass() == NullPointerException.class) {
                log.error("토큰이 비어있습니다.");
                errorCode = 401;
            } else if (ex.getClass() == ExpiredJwtException.class) {
                log.error("토큰이 만료되었습니다.");
                errorCode = 402;
            } else if (ex.getClass() == MalformedJwtException.class) {
                log.error("JWT 토큰 구조가 잘못되었습니다.");
                errorCode = 403;
            } else if (ex.getClass() == SignatureException.class) {
                log.error("변조된 토큰입니다.");
                errorCode = 404;
            } else if (ex.getClass() == UnsupportedJwtException.class) {
                log.error("JWT 형식이 잘못되었습니다.");
                errorCode = 405;
            }

            byte[] bytes = getErrorCode(errorCode).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Flux.just(buffer));
        }
    }

}