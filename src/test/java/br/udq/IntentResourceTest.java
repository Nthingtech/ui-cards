package br.udq;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(OpenAiStub.class)
class IntentResourceTest {
    @TestHTTPResource
    URI base;
    @Inject
    ShapeConfig config;

    @Test
    void passaPeloAdaptadorDoLangChain4jEDevolveOCard() {
        given().contentType("application/json").body("{\"text\":\"25 min de foco\",\"useCache\":false}")
                .post("/api/intent").then().statusCode(200)
                .body("source", equalTo("openai"), "prediction.intent", equalTo("TIMER"),
                        "prediction.probabilities.TIMER", equalTo(0.94f), "card.fields.seconds", equalTo(1500));
    }

    @Test
    void guardaNoCacheSoAsPrevisoesBemSucedidas() {
        given().contentType("application/json").body("{\"text\":\"7 minutos teste de cache\"}").post("/api/intent").then()
                .statusCode(200);
        given().contentType("application/json").body("{\"text\":\"7 minutos teste de cache\"}").post("/api/intent").then()
                .body("prediction.cached", equalTo(true), "prediction.inferenceMillis", equalTo(0));
    }

    @Test
    void respostaInvalidaDoModeloViraErroEmVezDeNota() {
        given().contentType("application/json").body("{\"text\":\"malformed input\",\"useCache\":false}")
                .post("/api/intent").then().statusCode(503);
    }

    @Test
    void validaAEntradaENaoChamaOModeloParaTextoVazio() {
        given().contentType("application/json").body("{}").post("/api/intent").then().statusCode(400);
        given().contentType("application/json").body("{\"text\":\"" + "x".repeat(501) + "\"}").post("/api/intent").then()
                .statusCode(400);
        given().contentType("application/json").body("{\"text\":\"\"}").post("/api/intent").then().statusCode(200)
                .body("source", equalTo("empty"));
    }

    @Test
    void escolhaManualNaoChamaOModelo() {
        int before = modelRequests();
        given().contentType("application/json").body("{\"text\":\"dividir 10 entre 3\",\"intent\":\"SPLIT\"}")
                .post("/api/parse").then().statusCode(200)
                .body("fields.perPerson", equalTo("3.33"), "fields.remainderCents", equalTo(1));
        given().contentType("application/json").body("{\"text\":\"qualquer coisa\",\"intent\":\"NONE\"}").post("/api/parse")
                .then().statusCode(400);
        assertEquals(before, modelRequests());
    }

    @Test
    void recusaChamadasSobrepostasAoModelo() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(base.resolve("api/intent")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"slow 25 minutos\",\"useCache\":false}")).build();
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        // As duas chamadas se sobrepõem dentro dos 600 ms do servidor falso. A ordem não importa.
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(Set.of(200, 429), Set.of(first.get().statusCode(), second.get().statusCode()));
    }

    @Test
    void serveOFrontendEmpacotado() {
        given().get("/").then().statusCode(200).body(containsString("Comece com um pensamento."), containsString("/static/"));
    }

    @Test
    void textoIncompletoNaoGeraCardNemPontuacaoInventada() {
        int before = modelRequests();
        for (String text : new String[] { "me lembra", "jantar com", "dividir", "comprar", "criar um", "eu quero ir para" }) {
            given().contentType("application/json").body(Map.of("text", text, "useCache", false))
                    .post("/api/intent").then().statusCode(200)
                    .body("source", equalTo("input-gate"), "ready", equalTo(false),
                            "prediction.intent", equalTo("NONE"), "prediction.probabilities.size()", equalTo(0),
                            "card", nullValue());
        }
        assertEquals(before, modelRequests(), "Texto incompleto não pode chegar à API do modelo");
    }

    @Test
    void confiancaAltaAindaPrecisaDeUmaDuracaoValida() {
        given().contentType("application/json").body("{\"text\":\"começar foco sem duração\",\"useCache\":false}")
                .post("/api/intent").then().statusCode(200)
                .body("prediction.intent", equalTo("TIMER"), "ready", equalTo(false),
                        "source", equalTo("openai"), "card", nullValue());
    }

    private int modelRequests() {
        String stubRoot = config.baseUrl().replaceFirst("/v1$", "");
        return Integer.parseInt(given().get(stubRoot + "/requests").then().statusCode(200).extract().asString());
    }
}