# Boas práticas para Java 21+

Este projeto é uma **biblioteca/API ORM** consumida por plugins Paper. Ele não é um plugin em si, portanto não possui `plugin.yml`, lifecycle de plugin nem dependência direta da API do Paper. Use **Java 21** como alvo de compilação para manter compatibilidade com o Paper 1.21.4+ e mantenha o código do ORM independente do servidor, a menos que o escopo de uma funcionalidade exija explicitamente integração com o Paper.

As seções seguintes descrevem boas práticas gerais de Java e de desenvolvimento para Paper; quando aplicadas a este projeto, considere o ponto de vista de uma biblioteca reutilizável, não de um plugin completo.

Referência: [Documentação do Paper sobre instalação do Java](https://docs.papermc.io/misc/java-install/)

---

## 1. Formatação e organização visual

1. **Não alinhe declarações manualmente com espaços.**

```java
// Evitar
private final PlayerService     playerService;
private final MessageRepository messageRepository;
private final EconomyService    economyService;

// Preferir
private final PlayerService playerService;
private final MessageRepository messageRepository;
private final EconomyService economyService;
```

2. Use **quatro espaços**, nunca tabs.

3. Use um formatador automático como fonte da verdade. Não ajuste espaços manualmente depois dele.

4. Use chaves mesmo para blocos de uma linha.

```java
if (!player.hasPermission(PERMISSION)) {
    return;
}
```

5. Evite múltiplas instruções na mesma linha.

6. Use limite preferencial de aproximadamente **120 caracteres**, mas não quebre código curto apenas para preencher um padrão.

7. Mantenha expressões curtas inline.

```java
return repository.findById(player.getUniqueId());
```

Não crie variáveis que não adicionam significado:

```java
// Desnecessário
var playerId = player.getUniqueId();
return repository.findById(playerId);
```

8. Quebre encadeamentos apenas quando eles ficarem difíceis de ler.

```java
return players.stream()
    .filter(Player::isOnline)
    .map(Player::getUniqueId)
    .toList();
```

9. Não use imports com `*`.

10. Comentários devem explicar **por que**, não repetir o código.

11. Não mantenha código comentado. O histórico pertence ao Git.

---

## 2. Simplicidade

1. A solução mais simples que atende corretamente ao requisito deve ser preferida.

2. Não crie abstrações para problemas que ainda não existem.

3. Não crie interfaces automaticamente para toda classe.

```java
// Desnecessário quando não existe uma fronteira real
interface RewardManager {
}

final class RewardManagerImpl implements RewardManager {
}
```

Use interfaces quando existir:

- mais de uma implementação;
- integração externa;
- contrato público;
- necessidade real de substituição em testes;
- separação entre domínio e infraestrutura.

4. Não transforme cada linha em um método.

```java
// Extração sem valor
private UUID getPlayerId(Player player) {
    return player.getUniqueId();
}
```

5. Não crie classes `Utils`, `Helper`, `Common` ou `Manager` para acumular operações desconexas.

Prefira nomes específicos:

```text
PlayerRepository
RewardCalculator
MessageRenderer
TeleportService
CooldownRegistry
```

6. Não use padrões de projeto apenas porque são conhecidos. O padrão deve resolver um problema concreto.

7. Prefira composição a herança.

8. Não generalize antes de existir pelo menos um caso real que justifique a generalização.

---

## 3. Responsabilidade das classes

Cada classe deve ter **uma responsabilidade e um motivo principal para mudar**.

Uma separação recomendada:

```text
ExamplePlugin         -> inicialização e encerramento
RewardCommand         -> entrada do comando
RewardListener        -> adaptação de eventos
RewardService         -> regra de negócio
RewardRepository      -> persistência
RewardMessageRenderer -> construção de mensagens
RewardConfig          -> configuração validada
Reward                -> modelo de dados
```

### Classe principal

A classe que estende `JavaPlugin` deve funcionar como **composition root**:

- criar dependências;
- conectar serviços;
- registrar listeners;
- registrar comandos;
- controlar o ciclo de vida.

Ela não deve conter regras de recompensa, SQL, menus ou processamento de eventos.

### Listener

O listener deve:

1. filtrar o evento;
2. extrair os dados necessários;
3. chamar o serviço correspondente.

```java
public final class PlayerJoinListener implements Listener {

    private final PlayerSessionService sessionService;

    public PlayerJoinListener(PlayerSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sessionService.openSession(event.getPlayer());
    }
}
```

### Command

O comando deve:

- validar sender;
- validar permissão;
- obter argumentos;
- chamar um caso de uso;
- apresentar o resultado.

Não coloque toda a regra de negócio dentro de `execute`.

### Repository

O repository deve lidar somente com persistência. Ele não deve enviar mensagens, acessar inventários ou aplicar regras de jogo.

---

## 4. Métodos e fluxo de controle

1. Cada método deve representar uma operação clara.

2. Evite profundidade excessiva de blocos.

```java
// Evitar
if (player != null) {
    if (player.isOnline()) {
        if (player.hasPermission(PERMISSION)) {
            giveReward(player);
        }
    }
}
```

```java
// Preferir
if (player == null || !player.isOnline()) {
    return;
}

if (!player.hasPermission(PERMISSION)) {
    return;
}

giveReward(player);
```

3. Use guard clauses para casos inválidos.

4. Evite mais de dois ou três níveis de aninhamento.

5. Evite parâmetros booleanos que alteram completamente o comportamento.

```java
// Evitar
teleport(player, location, true, false);
```

Prefira métodos ou tipos explícitos:

```java
teleportService.teleportSafely(player, location);
```

6. Para muitos parâmetros relacionados, use um `record`.

```java
public record RewardRequest(
    UUID playerId,
    String rewardId,
    int amount,
    RewardSource source
) {
}
```

7. Não altere o significado de uma variável ao longo do método.

8. Evite efeitos colaterais escondidos em getters, predicates e operações de stream.

9. Extraia condições que representem regras de negócio.

```java
if (isEligibleForDailyReward(player, profile)) {
    rewardService.grantDailyReward(player);
}
```

10. Não extraia condições triviais usadas uma única vez apenas para diminuir o método artificialmente.

---

## 5. Imutabilidade e modelagem

1. Campos devem ser `final` sempre que não precisarem ser substituídos.

2. Prefira objetos imutáveis.

3. Use `record` para:

- DTOs;
- resultados;
- configurações carregadas;
- comandos internos;
- snapshots;
- identificadores compostos.

4. Use `enum` para conjuntos fechados de valores.

5. Use classes e interfaces `sealed` quando a hierarquia for realmente fechada.

6. Retorne cópias imutáveis em limites públicos.

```java
public List<Reward> rewards() {
    return List.copyOf(rewards);
}
```

7. Retorne coleções vazias, não `null`.

8. Não exponha coleções mutáveis internas.

9. Tenha cuidado com objetos mutáveis da API, como `Location`, `ItemStack` e algumas estruturas de inventário. Faça cópia quando a propriedade do objeto não estiver clara.

10. Use `UUID` como identidade persistente do jogador. Nome não é identificador estável.

---

## 6. Recursos modernos do Java

1. Use `var` somente quando o tipo estiver evidente.

```java
var playerId = player.getUniqueId();
var rewards = new ArrayList<Reward>();
```

Evite quando o método não deixa o tipo claro:

```java
var result = processor.process(input);
```

2. Use switch expressions para mapeamentos fechados.

```java
return switch (status) {
    case ACTIVE -> Component.text("Ativo");
    case BLOCKED -> Component.text("Bloqueado");
    case EXPIRED -> Component.text("Expirado");
};
```

3. Use pattern matching para reduzir casts desnecessários.

```java
if (!(sender instanceof Player player)) {
    sender.sendMessage("Comando exclusivo para jogadores.");
    return;
}
```

4. Use text blocks para SQL e textos multilinha.

```java
var sql = """
    SELECT player_id, balance
    FROM player_balance
    WHERE player_id = ?
    """;
```

5. Use `Duration`, `Instant` e `Clock` em vez de números soltos representando tempo.

6. Use `try-with-resources` para arquivos, streams, statements e recursos fecháveis.

7. Use `Optional` principalmente como tipo de retorno para ausência legítima.

Evite:

- `Optional` como campo;
- `Optional` como parâmetro;
- `Optional.get()`;
- encadeamentos excessivos que escondem o fluxo.

8. Não use recursos preview em plugins distribuídos normalmente. No Java 25, algumas funcionalidades, como Structured Concurrency e patterns com tipos primitivos, ainda são preview e exigem configuração específica de compilação e execução.

Referência: [JDK 25](https://openjdk.org/projects/jdk/25/)

---

## 7. Threads e assincronismo no Paper

A maior parte da API Bukkit/Paper que acessa ou modifica mundo, entidades e estado do servidor não é segura fora da thread apropriada.

Trabalho assíncrono deve ser reservado principalmente para banco de dados, arquivos, HTTP e outras operações de I/O.

Referência: [Scheduler do Paper](https://docs.papermc.io/paper/dev/scheduler/)

### Pode executar fora da thread principal

- consultas SQL;
- chamadas HTTP;
- serialização pesada;
- leitura e escrita de arquivos;
- cálculos que não acessam objetos da API;
- processamento de DTOs imutáveis.

### Deve voltar ao scheduler antes de acessar

- `Player`;
- `Entity`;
- `World`;
- blocos;
- inventários;
- scoreboard;
- maioria das operações Bukkit/Paper.

### Regras

1. Nunca bloqueie a thread principal com:

```java
future.join();
future.get();
Thread.sleep(...);
```

2. Não passe eventos inteiros para tarefas assíncronas. Extraia apenas dados imutáveis.

3. Não mantenha uma referência de `Player` durante uma operação demorada. Armazene o `UUID` e resolva o jogador novamente na thread correta.

4. Não crie threads sem controle de ciclo de vida.

5. Use um executor controlado pelo plugin ou o scheduler assíncrono do Paper.

6. Encerre executores em `onDisable`.

7. Trate exceções de tarefas assíncronas. Não permita falhas silenciosas em `CompletableFuture`.

8. Virtual threads são adequadas para tarefas que passam a maior parte do tempo bloqueadas em I/O. Elas não tornam cálculos mais rápidos e não tornam a API Paper thread-safe.

Referência: [Virtual Threads no Java 25](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)

9. Para teleporte que pode carregar chunks, prefira `teleportAsync`, evitando carregamento síncrono de chunks na thread principal.

Referência: [Teleporte de entidades no Paper](https://docs.papermc.io/paper/dev/entity-teleport/)

10. Não marque `folia-supported: true` sem realmente usar os schedulers global, regional, de entidade e assíncrono corretamente.

Referência: [Suporte a Folia](https://docs.papermc.io/paper/dev/folia-support/)

---

## 8. Eventos

1. Use o evento mais específico disponível.

2. Use `ignoreCancelled = true` quando eventos cancelados não interessarem.

```java
@EventHandler(ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent event) {
}
```

3. Use prioridades com significado:

- `LOWEST`: preparação inicial;
- `LOW` e `NORMAL`: comportamento comum;
- `HIGH` e `HIGHEST`: decisões posteriores;
- `MONITOR`: observação final, sem alterar o evento.

4. Não faça consultas ao banco dentro do handler.

5. Não execute processamento pesado em eventos frequentes.

6. Em `PlayerMoveEvent`, verifique primeiro se o jogador realmente mudou de bloco ou região relevante.

7. Não crie um único listener com dezenas de eventos desconexos.

8. Retorne cedo quando o evento não for relevante.

9. Considere que outro plugin pode ter alterado ou cancelado o evento antes do seu listener.

Referência: [Event listeners no Paper](https://docs.papermc.io/paper/dev/event-listeners/)

---

## 9. Comandos e mensagens

1. Use a Command API baseada em Brigadier para comandos estruturados.

2. Use `BasicCommand` apenas para comandos realmente simples.

3. Deixe parsing e sugestões sob responsabilidade da API sempre que possível.

4. Sugestões não devem consultar banco de dados ou realizar I/O bloqueante.

5. Verifique permissões antes de executar trabalho caro.

6. Trate console, command block e jogador explicitamente.

7. Use Adventure `Component` e MiniMessage.

8. Não use `§`, `ChatColor` ou concatenação de códigos de cor legados. Adventure é a forma suportada pelo Paper para mensagens, nomes, lores, bossbars e outros textos.

Referência: [Component API do Paper](https://docs.papermc.io/paper/dev/component-api/introduction/)

9. Não desserialize entrada do usuário diretamente como MiniMessage. Use placeholders `unparsed` ou escape das tags.

10. Centralize mensagens e templates. Não espalhe textos pelo código.

O sistema moderno de comandos do Paper utiliza Brigadier; `BasicCommand` permanece apropriado para casos menores.

Referência: [Command API do Paper](https://docs.papermc.io/paper/dev/command-api/basics/introduction/)

---

## 10. Configuração e persistência

1. Leia a configuração uma vez e converta-a para objetos tipados e imutáveis.

```java
public record RewardConfig(
    Duration cooldown,
    int dailyAmount,
    boolean broadcast
) {
}
```

2. Valide a configuração durante a inicialização.

3. Falhe cedo ao encontrar valor obrigatório inválido.

4. Não faça chamadas profundas a `getConfig()` em eventos frequentes.

5. Não salve o arquivo de configuração a cada evento.

6. Use PDC para metadados próprios em entidades, itens e outros holders compatíveis.

7. Não use lore, nome do item ou NBT interno como banco de dados.

8. Use `NamespacedKey` estável e centralizada.

9. Versione formatos persistidos para permitir migrações.

10. Para grande quantidade de dados, use banco de dados.

11. Use prepared statements.

12. Use pool de conexões quando houver banco externo.

13. Execute operações de banco fora da thread principal.

14. Use migrations explícitas e versionadas.

15. Não registre senhas, tokens ou strings completas de conexão em logs.

PDC é a API apropriada para dados personalizados associados a objetos do jogo; bancos são recomendados para volumes maiores de dados.

Referência: [Persistent Data Container](https://docs.papermc.io/paper/dev/pdc/)

---

## 11. Ciclo de vida do plugin

### `onLoad`

- apenas preparação que realmente precisa acontecer antes do enable;
- não acessar APIs que ainda não estão disponíveis;
- evitar abrir recursos desnecessariamente.

### `onEnable`

- carregar e validar configuração;
- construir repositories e services;
- abrir recursos;
- registrar listeners;
- registrar comandos;
- iniciar tarefas.

### `onDisable`

- cancelar tarefas;
- impedir novos trabalhos;
- finalizar filas pendentes;
- salvar dados necessários;
- fechar banco;
- fechar executores;
- liberar recursos externos.

O Paper documenta `onDisable` como o ponto de limpeza de recursos, incluindo conexões e dados pendentes. O comando `/reload` está depreciado e uma reinicialização completa deve ser preferida.

Referência: [Como plugins funcionam no Paper](https://docs.papermc.io/paper/dev/how-do-plugins-work/)

---

## 12. Performance

1. Otimize após medir.

2. Não faça varredura de todos os jogadores ou entidades a cada tick sem necessidade.

3. Prefira eventos a polling.

4. Agrupe operações de persistência.

5. Use debounce para alterações muito frequentes.

6. Não carregue chunks sincronamente.

7. Não use `parallelStream()` dentro de código Paper.

8. Use streams para transformação legível; prefira loops em hot paths, early returns ou código com efeitos colaterais.

9. Não crie cache sem:

- limite;
- expiração;
- invalidação;
- estratégia de limpeza.

10. Evite micro-otimizações que prejudiquem clareza sem resultado medido.

11. Não mantenha referências estáticas a jogadores, mundos ou ao plugin.

12. Use profiling com Spark e, para análise mais profunda, Java Flight Recorder.

---

## 13. NMS e dependências internas

1. Use a API Paper antes de qualquer código interno.

2. Não use reflection ou NMS quando existir uma API pública equivalente.

3. Isole código NMS atrás de uma classe ou módulo específico.

4. Não espalhe classes `net.minecraft` pelo domínio.

5. Para Paper 26.1+, use mappings Mojang. Artefatos reobfuscados para mappings Spigot não funcionam nessa linha.

Referência: [Internals do Paper](https://docs.papermc.io/paper/dev/internals/)

---

## Regra prática para decidir entre inline, método ou classe

### Deixe inline quando

- a expressão for curta;
- for usada uma vez;
- não representar uma regra de negócio;
- não esconder complexidade.

### Crie uma variável quando

- o nome explicar a intenção;
- evitar cálculo repetido;
- facilitar depuração;
- reduzir uma condição difícil de ler.

### Extraia um método quando

- existir uma operação com nome próprio;
- a lógica for reutilizada;
- representar uma regra de negócio;
- puder ser testada isoladamente;
- reduzir aninhamento real.

### Crie uma classe quando

- existir estado próprio;
- existir ciclo de vida próprio;
- houver dependências próprias;
- a responsabilidade puder mudar independentemente;
- a lógica formar um componente coeso.


## Code style: Scope Bursting

Vertical flow over horizontal. Every line one cognitive intention. Code must be
visually scannable top-to-bottom; never compressed into a single dense line.

### 1. Scope bursting — always explode chained calls

Never resolve multiple responsibilities inline. Extract each step into a local
`var` with a semantic name. Hard limit: **two levels of chaining max** in any
single expression; beyond that, explode.

Bad:

```java
actor.sendSuccess(this.config.value().messages().spawnSet());
```

Good:

```java
var snap = this.config.value();
var messages = snap.messages();
var spawnSetMsg = messages.spawnSet();

actor.sendSuccess(spawnSetMsg);
```

Permitted as a single chain: short, semantically obvious calls like
`player.getUniqueId()` or `text.toLowerCase()`.

### 2. No inline logic in parameters

No `.replace()`, concatenation, ternaries, streams, builders, lambdas, or
formatting expressions directly inside a method call argument list. Extract
first, then pass the named variable.

Bad:

```java
sender.sendMessage(messages.prefix().replace("{player}", target.getName()));
```

Good:

```java
var prefix = messages.prefix();
var targetName = target.getName();
var formatted = prefix.replace("{player}", targetName);

sender.sendMessage(formatted);
```

### 3. Guard clauses (fail-fast)

Invalid state exits the method immediately. Never nest the happy path inside
conditionals; never use `else` after `return`. Validate in this order: null →
empty optionals → permissions → invalid state → preconditions → main flow.

Bad:

```java
if (player != null) {
  if (player.isOnline()) {
    process(player);
  }
}
```

Good:

```java
if (player == null || !player.isOnline()) {
  return;
}

process(player);
```

### 4. Zone segmentation

Every non-trivial method has these zones separated by a blank line:

1. **Extraction** — collect data (configs, services, current state).
2. **Validation** — guard clauses only.
3. **Processing** — main logic, object construction.
4. **Side effect** — messages, dispatch, logging, return.

### 5. Two-level indentation max

If a method needs deeper nesting, extract a helper, add a guard clause, or
linearize the flow. Pyramids of doom are forbidden.

### 6. Construction of complex objects

Builders and constructors with multiple non-trivial arguments build into a
named local first, then pass that local to the consumer.

Bad:

```java
scheduler.schedule(
    sender,
    location,
    delay,
    new DelayedTeleportPrompt(messages.teleporting(), messages.teleported()));
```

Good:

```java
var teleporting = messages.teleporting();
var teleported = messages.teleported();

var prompt = new DelayedTeleportPrompt(teleporting, teleported);

scheduler.schedule(sender, location, delay, prompt);
```

### 7. Streams

Long stream chains are exploded — collect intermediate `toList()` results into
named variables. Short pipelines (`stream().filter(...).toList()`) are fine
when each operation is semantically obvious.

### 8. Variable naming

Intermediate `var` names are encouraged and must carry intent. Use `snap` for
config snapshots, `messages` for the message bundle, `<noun>Msg` for the
rendered string. No single-letter or generic names except in tight loops.

### 9. Vertical spacing

Blank lines separate cognitive shifts — between extraction and validation,
between validation and processing, between processing and side effect, between
unrelated mutations.

### 10. Horizontal width

Target ~100 columns. If a line starts to grow, that is the signal to extract,
not to wrap. The formatter (Spotless / google-java-format) will not produce
good wrapping for chains; defend against it by exploding before it has to.
