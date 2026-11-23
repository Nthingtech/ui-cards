package br.udq;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Servidor falso que imita o POST /v1/decisions da OpenAI. Os testes passam pelo OpenAiDecisionModel de verdade,
 * mas sem gastar com a API nem precisar de chave.
 */
public class OpenAiStub implements QuarkusTestResourceLifecycleManager {
    private HttpServer server;
    private ExecutorService executor;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            var requests = new AtomicInteger();
            // Quantas vezes o "modelo" foi chamado (os testes usam para provar que algo NÃO chamou o modelo)
            server.createContext("/requests", exchange -> {
                byte[] body = Integer.toString(requests.get()).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/v1/decisions", exchange -> {
                requests.incrementAndGet();
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (request.contains("slow")) {
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                String chosen = request.contains("malformed") ? "UNEXPECTED" : "TIMER";
                byte[] body = ("""
                        {"model":"gpt-6-luna","answers":[
                          {"type":"choice","name":"intent","choice":"%s","confidence":0.88,"probabilities":[
                            {"value":"EVENT","probability":0.01},{"value":"REMINDER","probability":0.01},
                            {"value":"CHECKLIST","probability":0.01},{"value":"TIMER","probability":0.94},
                            {"value":"SPLIT","probability":0.01},{"value":"NOTE","probability":0.01},
                            {"value":"NONE","probability":0.01}]},
                          {"type":"predicate","name":"urgent","probability":0.02}],
                         "usage":{"input_tokens":120,"output_tokens":0,"total_tokens":120}}
                        """)
                        .formatted(chosen).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            return Map.of("card.base-url", url + "/v1", "card.api-key", "chave-de-teste");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }
}