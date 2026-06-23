# 🚀 PaperORM

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/downloads/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

O **PaperORM** é um framework ORM (Object-Relational Mapping) leve, de alto desempenho e assíncrono, projetado especificamente para **Plugins de Minecraft (Paper/Spigot)**. Desenvolvido com **Java 21** e **Gradle**, ele foca em simplicidade, segurança de concorrência e excelente experiência de desenvolvimento (DX).

---

## ✨ Funcionalidades Principais

* **⚡ Assincronia Nativa & Virtual Threads**: Suporte nativo completo a operações assíncronas baseadas em `CompletableFuture` utilizando as **Virtual Threads do Java 21** para evitar qualquer tipo de travamento na thread principal do servidor (*Main Thread Lag*).
* **📦 Mapeamento Avançado de Entidades**:
  * Anotações básicas: `@Entity`, `@Table`, `@Id`, `@Column`, `@Transient`.
  * Parâmetros de integridade: `@Column(unique = true, nullable = false, length = 64)`.
  * Relacionamentos: Mapeamento de chave estrangeira `@ManyToOne` com suporte a resoluções automáticas de entidade (*Referenced Shells*).
* **📈 Otimização de Performance**:
  * Anotação `@Index` para criação automática de índices de busca rápida em campos críticos de consulta (ex: UUID ou nome de jogadores), eliminando varreduras de tabela inteira (*Full Table Scans*).
  * Sistema de cache inteligente com Identity Map (`WeakReference`) com **autopodamento periódico de memória (Pruning)** para referências coletadas pelo Garbage Collector.
* **🌐 Conectividade & Pool de Conexões**:
  * Pool de alto desempenho embarcado usando **HikariCP**.
  * Suporte nativo a **SQLite** (banco de dados local zero-setup) e **MySQL** (para redes de servidores BungeeCord/Velocity).
* **🔍 Query Builder Fluente**:
  * API de busca declarativa encadeada (`eq()`, `notEq()`, `greaterThan()`, `lessThan()`, `like()`, `isNull()`, `isNotNull()`, `in()`, `limit()`, `orderBy()`).
* **🔄 Gerenciador de Transações & Migrations**:
  * Lógica simplificada de Migrations com `MigrationRunner` para controle de versões de schema.
  * Transações ACID seguras via `runInTransaction()`.

---

## 🏛️ Estrutura de Pacotes (DX-Oriented)

A biblioteca expõe uma API limpa no pacote raiz. As implementações internas de baixo nível ficam organizadas em subpacotes específicos:

```text
com.github.paperorm
├── PaperOrm.java               # Builder e ponto de entrada da API
├── OrmSession.java             # Gerenciador de sessão e cache
├── OrmFactory.java             # Criação interna de repositórios
├── annotation/                 # @Entity, @Table, @Column, @Id, @Index, @ManyToOne, @Transient
├── database/                   # Conexão remota, local, transações e HikariCP
├── dialect/                    # Dialetos de banco de dados (SQLite, MySQL)
├── exception/                  # OrmException (Sealed), MappingException, ConnectionException
├── mapping/                    # Mapeadores de tipos (TypeConverter), metadados e reflection
├── migration/                  # Mapeador de versionamento de schemas (Migration)
├── repository/                 # Interfaces Repository<T>, Query<T> e suas implementações SQL
└── schema/                     # SchemaManager responsável por DDL e alteração de tabelas
```

---

## 🛠️ Instalação & Build

Para compilar o projeto localmente:

```bash
./gradlew build
```

O arquivo JAR gerado estará localizado em `build/libs/paper-orm-1.0.0-SNAPSHOT.jar`.

Para publicar o artefato no repositório Maven local:

```bash
./gradlew publishToMavenLocal
```

---

## 📖 Guia de Uso

### 1. Definição da Entidade

```java
package com.github.paperorm.profiles;

import com.github.paperorm.annotation.*;
import java.util.UUID;

@Entity
@Table(name = "player_profiles")
public final class PlayerProfile {

    @Id(autoIncrement = true)
    @Column(nullable = false)
    private Long id;

    @Column(unique = true, nullable = false)
    @Index(name = "idx_profiles_uuid") // Cria automaticamente um índice no banco de dados
    private UUID uuid;

    @Column(nullable = false, length = 64)
    private String name;

    @Column
    private int coins;

    @Transient
    private boolean isOnline; // Ignorado pelo banco de dados

    // Construtor sem argumentos exigido pelo ORM
    public PlayerProfile() {}

    public PlayerProfile(UUID uuid, String name, int coins) {
        this.uuid = uuid;
        this.name = name;
        this.coins = coins;
    }

    // Getters e Setters...
}
```

### 2. Inicialização do ORM

#### Usando SQLite (Ideal para servidor único)
```java
PaperOrm paperOrm = PaperOrm.builder()
    .sqlite(plugin.getDataFolder().toPath().resolve("database.db"))
    .useVirtualThreads() // Habilita processamento leve de tarefas de IO de banco (Java 21)
    .registerEntity(PlayerProfile.class)
    .autoCreateTables(true)
    .build();
```

#### Usando MySQL (Ideal para redes de servidores)
```java
PaperOrm paperOrm = PaperOrm.builder()
    .mysql("127.0.0.1", 3306, "minecraft_db", "admin", "secretPassword")
    .useVirtualThreads()
    .registerEntity(PlayerProfile.class)
    .autoCreateTables(true)
    .build();
```

---

### 3. Executando Operações CRUD

#### Escrita e Leitura Básica
```java
var repository = paperOrm.getRepository(PlayerProfile.class);

// Salvar perfil de forma síncrona
var profile = new PlayerProfile(UUID.randomUUID(), "Gamer123", 500);
repository.save(profile); // O ID auto-incrementado é populado na instância automaticamente

// Buscar perfil por ID
Optional<PlayerProfile> found = repository.findById(profile.getId());
```

#### Operações Assíncronas (Recomendado para Plugins de Minecraft)
```java
// Salvando e buscando dados sem travar a Main Thread do Minecraft
repository.saveAsync(profile)
    .thenCompose(v -> repository.findByIdAsync(profile.getId()))
    .thenAccept(optProfile -> {
        optProfile.ifPresent(p -> {
            plugin.getLogger().info("Perfil " + p.getName() + " carregado assincronamente!");
        });
    });
```

---

### 4. Query Builder Fluente

O construtor de consultas fornece uma API limpa para buscar dados de forma flexível:

```java
// Busca perfis com moedas maiores que 1000 e nome igual a "Gamer123"
List<PlayerProfile> richPlayers = repository.select()
    .where("coins").greaterThan(1000)
    .and("name").eq("Gamer123")
    .orderBy("coins", "DESC")
    .limit(10)
    .list();

// Busca perfis com moedas nulas ou que estão contidas em uma lista específica
List<PlayerProfile> targetedPlayers = repository.select()
    .where("coins").isNull()
    .or("name").in("Gamer1", "Gamer2", "Gamer3")
    .list();
```

---

### 5. Controle de Transações

O controle ACID permite aplicar rollback de transações caso alguma operação falhe:

```java
paperOrm.openSession().runInTransaction(conn -> {
    var profile1 = repository.findById(1L).orElseThrow();
    var profile2 = repository.findById(2L).orElseThrow();
    
    profile1.setCoins(profile1.getCoins() - 100);
    profile2.setCoins(profile2.getCoins() + 100);
    
    repository.update(profile1);
    repository.update(profile2);
    
    return true; // Commit automático. Se uma exceção for lançada, o Rollback é aplicado
});
```

---

### 6. Sistema de Migrations

```java
MigrationRunner runner = new MigrationRunner();
runner.run(paperOrm.connection(), List.of(
    new Migration(1, "create tables", """
        CREATE TABLE IF NOT EXISTS player_profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid TEXT NOT NULL,
            name TEXT NOT NULL,
            coins INTEGER NOT NULL
        )
        """),
    new Migration(2, "add rank column", "ALTER TABLE player_profiles ADD COLUMN rank TEXT DEFAULT 'default'")
));
```

---

## 🎮 Boas Práticas para PaperSpigot Plugins

1. **Nunca bloqueie a thread principal**: Sempre execute operações de escrita/leitura usando as rotas assíncronas do ORM (`saveAsync()`, `findByIdAsync()`, etc.).
2. **Retorno seguro para tarefas no mundo**: Ao obter resultados de banco assíncronos, utilize o Scheduler do Bukkit/Paper caso precise realizar interações diretas com o jogo (como teletransportar jogadores ou spawnar entidades):
   ```java
   repository.findByIdAsync(id).thenAccept(optProfile -> {
       optProfile.ifPresent(profile -> {
           // Retorna de forma segura para a thread síncrona
           Bukkit.getScheduler().runTask(plugin, () -> {
               Player player = Bukkit.getPlayer(profile.getUuid());
               if (player != null) player.giveExp(profile.getCoins());
           });
       });
   });
   ```
3. **Sombreamento (Shading & Relocation)**: No Gradle, utilize o plugin `Shadow` e configure a realocação das dependências internas do PaperORM (como o `HikariCP`) para evitar conflitos de classpath na JVM com outros plugins do servidor:
   ```kotlin
   tasks.shadowJar {
       relocate("com.zaxxer.hikari", "com.github.paperorm.libs.hikari")
   }
   ```
4. **Fechamento de conexões**: Lembre-se de sempre invocar `paperOrm.close()` no método `onDisable()` do seu plugin de Minecraft para liberar de forma limpa todas as conexões abertas com o banco de dados JDBC.

---

## 📄 Licença

Este projeto é distribuído sob a licença **MIT**. Veja o arquivo `LICENSE` para mais informações.
