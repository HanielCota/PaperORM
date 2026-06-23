<div align="center">

# 🔌 PaperORM

### *Um framework ORM leve, assíncrono, robusto e de alto desempenho projetado sob medida para plugins PaperSpigot.*

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Platform-PaperSpigot-cyan?style=for-the-badge&logo=minecraft&logoColor=white" alt="PaperSpigot" />
  <img src="https://img.shields.io/badge/Build-Gradle-blue?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle" />
  <img src="https://img.shields.io/badge/JitPack-v1.0.0-green?style=for-the-badge&logo=github" alt="JitPack v1.0.0" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License MIT" />
</p>

---

[✨ Funcionalidades](#-funcionalidades) • [🏛️ Pacotes](#️-pacotes) • [📦 Instalação](#-instalação) • [📖 Guia de Uso](#-guia-de-uso) • [🎮 Boas Práticas](#-boas-práticas-para-plugins) • [📄 Licença](#-licença)

</div>

---

## ✨ Funcionalidades

* ⚡ **Threads Virtuais do Java 21**: Execução assíncrona real de queries e conexões de banco de dados, liberando 100% da thread de tick do Minecraft (Main Thread) para mitigar quedas de TPS.
* 🔒 **Segurança Nativa & Sanitização**: Proteção integrada contra SQL Injection por meio de Prepared Statements sistemáticos e sanitização dinâmica de identificadores através de aspas automáticas nos dialetos de banco.
* 🏷️ **Mapeamento Declarativo Avançado**:
  * `@Index` para criação e sincronização automáticas de índices de banco em colunas de alta frequência de busca (ex: UUIDs e nicknames).
  * `@Column(unique = true)` para integridade referencial nativa no banco.
  * Mapeamento de objetos complexos (JSON) usando conversor automático baseado em **Gson**.
* 🔄 **Transações Assíncronas**: APIs fluidas `runInTransactionAsync` com suporte automático a rollback integrado e limpeza de cache de primeiro nível.
* 🚀 **Eventos de Ciclo de Vida**: Callbacks embutidos via anotações (`@PrePersist`, `@PostLoad`, `@PreDelete`) que automatizam fluxos sem poluir os repositórios.
* 📂 **Migrações Automáticas**: Gerenciador de migrações em lote para ler e aplicar arquivos de migração SQL (`V1.sql`, `V2.sql`, etc.) direto do Classpath (pasta resources) ou do diretório do plugin.
* 🏛️ **Repositórios Estendíveis**: Classe utilitária `AbstractRepository<T>` protegida para facilitar a codificação de repositórios customizados com regras de negócios específicas do plugin.
* 🔌 **Integração de Logs**: Canalize os logs de depuração e conexões do ORM diretamente para o Logger do plugin Bukkit (`plugin.getLogger()`).
* 🌐 **SQLite & MySQL**: Perfeito tanto para banco local embutido (SQLite em WAL mode) quanto para bancos externos distribuídos em rede (MySQL integrado ao pool HikariCP).

---

## 🏛️ Estrutura de Pacotes

Projetado focando em **DX** (*Developer Experience*), a API expõe as classes principais na raiz e isola as partes de engenharia interna:

```text
com.github.paperorm
├── PaperOrm.java               # Ponto de entrada, builder e configuração fluente
├── OrmSession.java             # Gerenciamento de sessão de cache local e transações
├── OrmFactory.java             # Factory interna dos repositórios
├── annotation/                 # Mapeamento do banco (@Entity, @Column, @Id, @Index, @ManyToOne...)
├── database/                   # Pools de conexão HikariCP, transações e adaptadores
├── dialect/                    # Definições e escape de dialetos (SQLite, MySQL)
├── exception/                  # Exceções seladas (Sealed hierarchy) para integridade
├── mapping/                    # Metadados de reflection e conversores (TypeConverter)
├── migration/                  # Controle, versionamento e loaders de migração SQL
├── repository/                 # Interfaces CRUD, query builder e AbstractRepository
└── schema/                     # Gerenciador de DDL e sincronizador de tabelas (SchemaManager)
```

---

## 📦 Instalação

Adicione o repositório do JitPack e a dependência do **PaperORM** no arquivo de build (`build.gradle.kts` ou `pom.xml`):

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.HanielCota:PaperORM:v1.0.0")
}
```

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.HanielCota:PaperORM:v1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.HanielCota</groupId>
    <artifactId>PaperORM</artifactId>
    <version>v1.0.0</version>
</dependency>
```

---

## 📖 Guia de Uso

### 1. Definindo as Entidades

```java
import com.github.paperorm.annotation.*;
import java.util.UUID;

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

    public PlayerProfile() {} // Construtor sem argumentos obrigatório
    
    // Getters & Setters
}
```

### 2. Inicialização Rápida & Logs com Bukkit

Configure o ORM no seu plugin conectando-o ao logger oficial do Bukkit:

```java
PaperOrm orm = PaperOrm.builder()
    .sqlite(plugin.getDataFolder().toPath().resolve("database.db"))
    .logger(plugin.getLogger()) // Todos os logs canalizados no console do Bukkit!
    .useVirtualThreads()       // Ativa execução paralela rápida do Java 21
    .registerEntity(PlayerProfile.class)
    .autoCreateTables(true)
    .build();
```

---

### 3. Migrações Automáticas de Schema

Mantenha o banco atualizado carregando arquivos `.sql` (`V1.sql`, `V2.sql`, etc.) presentes no Classpath (pasta resources):

```java
var migrations = MigrationRunner.loadFromClasspath(
    plugin.getClass().getClassLoader(), 
    "db/migrations"
);

PaperOrm orm = PaperOrm.builder()
    .sqlite(databasePath)
    .migrations(migrations) // Executa as migrações automaticamente ao iniciar
    .build();
```

---

### 4. Transações Assíncronas

Execute alterações em lote de forma transacional e assíncrona para garantir consistência de dados:

```java
orm.openSession().runInTransactionAsync(conn -> {
    // Executa operações seguras de escrita
    profileRepository.save(profile);
    statsRepository.save(stats);
    return null; // Retorna nulo ou algum valor obtido
}).thenAccept(v -> {
    plugin.getLogger().info("Transação em lote executada com sucesso!");
}).exceptionally(err -> {
    plugin.getLogger().severe("A transação falhou e o rollback foi efetuado: " + err.getMessage());
    return null;
});
```

---

### 5. Repositórios Estendíveis (`AbstractRepository`)

Crie repositórios dedicados para encapsular queries de domínio e lógica de negócios customizada:

```java
import com.github.paperorm.PaperOrm;
import com.github.paperorm.repository.AbstractRepository;
import java.util.Optional;
import java.util.UUID;

public final class PlayerRepository extends AbstractRepository<PlayerProfile> {

    public PlayerRepository(PaperOrm orm) {
        super(PlayerProfile.class, orm);
    }

    public Optional<PlayerProfile> findByUuid(UUID uuid) {
        return select()
            .where("uuid").eq(uuid)
            .uniqueResult();
    }
}
```

---

### 6. Query Builder Fluente

```java
var repository = orm.getRepository(PlayerProfile.class);

// Busca combinada com ordenação, IS NOT NULL, limite e paginação
List<PlayerProfile> leaders = repository.select()
    .where("coins").greaterThan(1000)
    .and("active").isNotNull()
    .orderBy("coins", "DESC")
    .limit(10)
    .offset(0) // Paginação suportada!
    .list();

// Busca usando operador IN
List<PlayerProfile> selected = repository.select()
    .where("name").in("Haniel", "Cota", "Paper")
    .list();
```

---

### 7. Eventos de Ciclo de Vida (Callbacks)

Crie métodos anotados dentro das suas entidades para executar lógica automaticamente antes ou depois de operações no banco de dados. Muito útil para inicializações ou sincronizações:

```java
@Entity
@Table(name = "players")
public class PlayerProfile {

    @PrePersist
    private void onPrePersist() {
        // Chamado sempre antes de ser salvo ou atualizado no banco
        if (this.uuid == null) this.uuid = UUID.randomUUID();
    }

    @PostLoad
    private void onPostLoad() {
        // Chamado sempre após a entidade ser carregada do banco
        this.isOnline = Bukkit.getPlayer(this.uuid) != null;
    }

    @PreDelete
    private void onPreDelete() {
        // Chamado antes de excluir o registro do banco de dados
    }
}
```

---

## 🎮 Boas Práticas para Plugins

* 📌 **Main Thread Livre**: Prefira utilizar operações que terminam com `Async` (`saveAsync()`, `findByIdAsync()`, etc.) para assegurar que a thread principal de tick do Minecraft permaneça livre de latência.
* 📌 **Retorno Seguro ao Jogo**: Ao lidar com manipulações do mundo do Minecraft após obter dados de queries assíncronas, retorne o processo para a Thread Principal utilizando o Scheduler do Bukkit:
  ```java
  repository.findByIdAsync(id).thenAccept(opt -> {
      opt.ifPresent(p -> {
          Bukkit.getScheduler().runTask(plugin, () -> {
              Player player = Bukkit.getPlayer(p.getUuid());
              if (player != null) {
                  player.setLevel(p.getCoins());
              }
          });
      });
  });
  ```
* 📌 **Relocação no Gradle (Shadow)**: Sempre use o plugin Shadow do Gradle para realocar (*relocate*) as classes internas do pool de conexão `HikariCP` e o driver de banco de dados. Isso previne conflitos de classpath com outros plugins rodando no mesmo servidor:
  ```kotlin
  tasks.shadowJar {
      relocate("com.zaxxer.hikari", "com.github.paperorm.libs.hikari")
  }
  ```
* 📌 **Fechamento Limpo**: Chame `paperOrm.close()` no método `onDisable()` do seu plugin para desativar e fechar todas as conexões ativas do pool HikariCP.

---

## 📄 Licença

Distribuído sob a licença **MIT**. Veja o arquivo `LICENSE` para mais detalhes.
