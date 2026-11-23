package br.udq;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "card")
public interface ShapeConfig {
    /** Chave da API da OpenAI. Optional porque o app funciona sem ela (só sem classificação automática). */
    Optional<String> apiKey();

    /** Endereço da API. Os testes trocam por um servidor falso. */
    @WithDefault("https://api.openai.com/v1")
    String baseUrl();

    @WithDefault("gpt-6-luna")
    String model();

    @WithDefault("PT10S")
    Duration timeout();

    @WithDefault("America/Sao_Paulo")
    String zone();
}