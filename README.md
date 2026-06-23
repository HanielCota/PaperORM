<div align="center">

# 🔌 PaperORM

### *Um framework ORM leve, assíncrono e de alto desempenho para plugins PaperSpigot.*

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Platform-PaperSpigot-cyan?style=for-the-badge&logo=minecraft&logoColor=white" alt="PaperSpigot" />
  <img src="https://img.shields.io/badge/Build-Gradle-blue?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License MIT" />
</p>

---

[✨ Funcionalidades](#-funcionalidades) • [🏛️ Pacotes](#️-pacotes) • [📖 Guia de Uso](#-guia-de-uso) • [🎮 Boas Práticas](#-boas-práticas-para-plugins) • [📄 Licença](#-licença)

</div>

---

## ✨ Funcionalidades

* ⚡ **Assincronia Nativa & Virtual Threads**: Execução paralela de banco utilizando as **Virtual Threads do Java 21** para eliminar completamente quedas de TPS na thread de tick principal do Minecraft.
* 📈 **Performance Otimizada**:
  * Anotação `@Index` para criação automática de índices de busca rápida em campos como UUIDs ou nomes.
  * Cache inteligente baseando-se em `WeakReference` com sistema automático de limpeza (*autopodamento*) de referências obsoletas da JVM.
* 🔒 **Integridade Total de Dados**: Validação de parâmetros nulos e suporte a restrição `UNIQUE` em campos `@Column`.
* 🌐 **SQLite & MySQL**: Escolha ideal para ambientes locais (SQLite embarcado) ou ambientes distribuídos em redes de servidores (MySQL integrado com pool HikariCP).
* 🔍 **Query Builder Fluente**: API declarativa avançada contendo operadores como `isNull()`, `isNotNull()` e `in()` sem necessidade de queries SQL brutas.

---

## 🏛️ Estrutura de Pacotes

A estrutura foi reorganizada visando o conceito de DX (*Developer Experience*), expondo a API pública diretamente no pacote raiz e isolando as implementações:

```text
com.github.paperorm
├── PaperOrm.java               # Configuração fluente e ponto de entrada da API
├── OrmSession.java             # Gerenciamento de sessão ativa e cache
├── OrmFactory.java             # Criação interna de repositórios
├── annotation/                 # Mapeamento do banco (@Entity, @Column, @Id, @Index, @ManyToOne...)
├── database/                   # Manipulação de conexões, pools e transações ACID
├── dialect/                    # Definições de dialetos SQL (SQLite, MySQL)
├── exception/                  # OrmException (Sealed) e exceções filhas especializadas
├── mapping/                    # Conversores de tipo (TypeConverter), metadados e reflection
├── migration/                  # Controle simples de versionamento do schema
├── repository/                 # Interfaces e implementações de queries e dados
└── schema/                     # SchemaManager responsável por DDL e alteração de tabelas
```

---

## 📖 Guia de Uso

### 1. Definindo as Entidades

```java
@Entity
@Table(name = "players")
public final class PlayerProfile {

    @Id(autoIncrement = true)
    @Column(nullable = false)
    private Long id;

    @Column(unique = true, nullable = false)
    @Index(name = "idx_players_uuid")
    private UUID uuid;

    @Column(nullable = false, length = 16)
    private String name;

    @Column
    private int coins;

    @Transient
    private boolean isOnline; // Ignorado pelo ORM

    public PlayerProfile() {} // Construtor obrigatório
}
```

### 2. Inicialização Rápida

#### SQLite (Servidor Único)
```java
PaperOrm orm = PaperOrm.builder()
    .sqlite(plugin.getDataFolder().toPath().resolve("database.db"))
    .useVirtualThreads() // Ativa o executor de threads virtuais
    .registerEntity(PlayerProfile.class)
    .autoCreateTables(true)
    .build();
```

#### MySQL (Redes Integradas / Bungee)
```java
PaperOrm orm = PaperOrm.builder()
    .mysql("127.0.0.1", 3306, "minecraft", "admin", "password")
    .useVirtualThreads()
    .registerEntity(PlayerProfile.class)
    .autoCreateTables(true)
    .build();
```

---

### 3. Operações CRUD Assíncronas

```java
var repository = orm.getRepository(PlayerProfile.class);

// Salva e carrega dados sem travar a Main Thread
repository.saveAsync(profile)
    .thenCompose(v -> repository.findByIdAsync(profile.getId()))
    .thenAccept(optProfile -> {
        optProfile.ifPresent(p -> {
            plugin.getLogger().info("Perfil do jogador " + p.getName() + " carregado com sucesso!");
        });
    });
```

---

### 4. Consultas Fluentes (Query Builder)

```java
// Consulta combinada com ordenação e limite de registros
List<PlayerProfile> leaders = repository.select()
    .where("coins").greaterThan(1000)
    .and("active").isNotNull()
    .orderBy("coins", "DESC")
    .limit(5)
    .list();

// Consulta usando múltiplos valores através de IN
List<PlayerProfile> targetList = repository.select()
    .where("id").in(1L, 2L, 5L)
    .list();
```

---

## 🎮 Boas Práticas para Plugins

* 📌 **Main Thread Livre**: Utilize sempre operações que terminam com `Async` (`saveAsync()`, `findByIdAsync()`, etc.) para assegurar que a thread do loop de tick do Minecraft permaneça livre de latência de rede ou disco.
* 📌 **Retorno ao Jogo Seguro**: Ao trabalhar com dados de forma assíncrona, certifique-se de usar o Bukkit Scheduler para rodar tarefas do mundo (como aplicar teletransportes ou editar blocos) na thread principal:
  ```java
  repository.findByIdAsync(id).thenAccept(opt -> {
      opt.ifPresent(p -> {
          Bukkit.getScheduler().runTask(plugin, () -> {
              Player player = Bukkit.getPlayer(p.getUuid());
              if (player != null) player.setLevel(p.getCoins());
          });
      });
  });
  ```
* 📌 **Relocação no Gradle**: Lembre-se de usar a realocação (*relocation*) no plugin Shadow do Gradle para evitar conflitos das classes empacotadas do pool `HikariCP` com outros plugins ativos no servidor:
  ```kotlin
  tasks.shadowJar {
      relocate("com.zaxxer.hikari", "com.github.paperorm.libs.hikari")
  }
  ```
* 📌 **Fechamento Limpo**: Chame `paperOrm.close()` no método `onDisable()` para liberar as conexões JDBC ativas da JVM antes que o plugin seja recarregado.

---

## 📄 Licença

Distribuído sob a licença **MIT**. Veja o arquivo `LICENSE` para mais detalhes.
