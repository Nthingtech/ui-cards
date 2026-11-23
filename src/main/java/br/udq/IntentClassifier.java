package br.udq;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;

import org.jboss.logging.Logger;

import dev.langchain4j.model.openai.OpenAiDecisionModel;
import dev.langchain4j.service.decision.DecisionServices;

@ApplicationScoped
public class IntentClassifier {
    private static final Logger LOG = Logger.getLogger(IntentClassifier.class);
    private static final int CACHE_SIZE = 128;

    /** Fica null quando não há chave da OpenAI: o app sobe, mas sem classificação automática. */
    private final CardDecisions decisions;
    private final ShapeConfig config;
    // Uma chamada ao modelo por vez: evita gastar com requisições sobrepostas enquanto a pessoa digita.
    private final Semaphore inference = new Semaphore(1);
    private final Map<String, Prediction> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true);

    public record Prediction(Intent intent, Map<Intent, Double> probabilities, Double confidence,
                             double margin, double urgent, String model, long inferenceMillis, boolean cached) {
        static Prediction waiting(String model) {
            // Resultado de política (texto incompleto), por isso não carrega probabilidades do modelo.
            return new Prediction(Intent.NONE, Map.of(), null, 0, 0, model, 0, false);
        }

        Prediction cacheHit() {
            return new Prediction(intent, probabilities, confidence, margin, urgent, model, 0, true);
        }
    }

    public IntentClassifier(ShapeConfig config) {
        this.config = config;
        this.decisions = config.apiKey()
                .filter(key -> !key.isBlank())
                .map(key -> DecisionServices.create(CardDecisions.class, OpenAiDecisionModel.builder()
                        .baseUrl(config.baseUrl())
                        .apiKey(key)
                        .modelName(config.model())
                        .timeout(config.timeout())
                        .maxRetries(0)
                        .build()))
                .orElse(null);
        if (decisions == null) {
            LOG.warn("OPENAI_API_KEY não definida: a classificação automática está desligada. "
                    + "A escolha manual de cards continua funcionando.");
        }
    }

    Prediction classify(String text, boolean useCache) {
        if (decisions == null) {
            throw new WebApplicationException("Defina OPENAI_API_KEY para ativar a classificação automática.", 503);
        }
        // Só a decisão do modelo vai para o cache. As datas são recalculadas a cada vez, com o relógio de hoje.
        if (useCache) {
            synchronized (cache) {
                var hit = cache.get(text);
                if (hit != null) {
                    return hit.cacheHit();
                }
            }
        }
        if (!inference.tryAcquire()) {
            throw new WebApplicationException("O modelo está ocupado; tente de novo com o texto mais recente.", 429);
        }
        try {
            long started = System.nanoTime();
            var result = decisions.classify(text);
            var choice = result.intent();
            var prediction = new Prediction(choice.value(), choice.probabilities(), choice.confidence(),
                    choice.margin(), result.urgent().probability(), config.model(),
                    (System.nanoTime() - started) / 1_000_000, false);
            if (useCache) {
                synchronized (cache) {
                    cache.put(text, prediction);
                    if (cache.size() > CACHE_SIZE) {
                        // Ordem de acesso: o primeiro da fila é o usado há mais tempo.
                        cache.remove(cache.keySet().iterator().next());
                    }
                }
            }
            return prediction;
        } catch (RuntimeException e) {
            LOG.warnf("Falha na classificação pela OpenAI: %s", e.getClass().getSimpleName());
            throw new WebApplicationException("A classificação pela OpenAI falhou. Verifique a chave, a conexão e o modelo.",
                    503);
        } finally {
            inference.release();
        }
    }
}