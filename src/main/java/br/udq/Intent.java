package br.udq;

import dev.langchain4j.model.output.structured.Description;

/** As descrições são o que o modelo lê para escolher o card. */
public enum Intent {
    @Description("Agendar um compromisso, reunião, refeição, ligação ou encontro, geralmente com outra pessoa")
    EVENT,
    @Description("Pedir para ser lembrado de fazer uma tarefa, como pagar o aluguel ou ligar para alguém")
    REMINDER,
    @Description("Listar várias tarefas separadas ou itens para comprar")
    CHECKLIST,
    @Description("Iniciar um timer ou uma sessão de foco com uma duração, sem agendar um evento no calendário")
    TIMER,
    @Description("Dividir um valor em dinheiro entre pessoas, incluindo rachar uma conta")
    SPLIT,
    @Description("Um pensamento, ideia, observação ou outro texto completo que não se encaixa nos outros cards")
    NOTE,
    @Description("Um fragmento inacabado, com pouca informação para escolher um card")
    NONE
}