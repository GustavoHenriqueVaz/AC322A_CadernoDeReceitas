# 📒 Caderno de Receitas

Aplicativo Android para **descobrir receitas na internet e montar o seu próprio caderno de receitas**, com anotações pessoais (nota, observações, tempo de preparo e foto do prato) que ficam salvas no aparelho e continuam disponíveis **mesmo sem conexão**.

O repositório tem **dois módulos Android** (duas entregas da disciplina):

| Módulo | Entrega | Tecnologia de interface | Dados |
|--------|---------|-------------------------|-------|
| `:app` | **Etapa 2** (app completo) | Jetpack Compose + Material 3 | API TheMealDB + Room + DataStore |
| `:parcial` | **Parcial** (Etapa 1) | Vistas XML + ViewBinding | Dados simulados (mocks) |

---

## 📑 Índice

1. [Objetivo da aplicação](#-1-objetivo-da-aplicação)
2. [Como rodar o projeto localmente](#-2-como-rodar-o-projeto-localmente)
3. [Bibliotecas externas utilizadas](#-3-bibliotecas-externas-utilizadas)
4. [Estrutura dos módulos e das pastas](#-4-estrutura-dos-módulos-e-das-pastas)
5. [Arquitetura](#-5-arquitetura)
6. [Política de cache e modo offline](#-6-política-de-cache-e-modo-offline)
7. [Fluxo completo do app (Etapa 2)](#-7-fluxo-completo-do-app-etapa-2)
8. [Rastreabilidade: requisito do professor → onde foi atendido](#-8-rastreabilidade-requisito-do-professor--onde-foi-atendido)
9. [Decisões técnicas](#-9-decisões-técnicas)
10. [Acessibilidade](#-10-acessibilidade)
11. [Segurança](#-11-segurança)
12. [Testes](#-12-testes)
13. [Limitações conhecidas](#-13-limitações-conhecidas)
14. [Créditos e fonte dos dados](#-14-créditos-e-fonte-dos-dados)

---

## 🎯 1. Objetivo da aplicação

O **Caderno de Receitas** resolve um problema simples: *achar uma receita, decidir se vale a pena e guardar o que você aprendeu ao cozinhá-la*.

- **Explorar:** busca receitas por nome (e por categoria) na API pública [TheMealDB](https://www.themealdb.com/api.php), com uma sugestão de "Receita do dia" e a opção de receber um **lembrete diário** por notificação.
- **Ver detalhes:** ingredientes (com marcação do que já foi separado), modo de preparo, vídeo e compartilhamento.
- **Salvar no caderno:** a receita fica guardada no aparelho (banco Room).
- **Anotar:** o usuário registra **nota de 1 a 5 estrelas, observações, tempo de preparo e uma foto do próprio prato**, em um formulário com validação.
- **Persistir:** tudo sobrevive à rotação da tela, ao fechamento do app e à reinicialização do aparelho; o caderno funciona 100% offline.

O módulo `:parcial` é a primeira entrega: um mini-catálogo de receitas fictícias com **duas telas em Vistas XML**, navegação por Intent explícito e ViewBinding.

---

## 🚀 2. Como rodar o projeto localmente

Você só precisa do **Android Studio**. Não é preciso instalar Gradle, Kotlin nem configurar nenhuma chave: o projeto traz o *Gradle Wrapper* (`gradlew`) e roda sem segredos.

### 2.1 Pré-requisitos

| Item | Detalhe |
|------|---------|
| **Android Studio** | Versão recente, que suporte o **Android Gradle Plugin 9.4.1** e o **Gradle 9.8.1** (o Android Studio avisa se o projeto exigir uma versão mais nova; use *Help → Check for Updates*). |
| **JDK 17 ou superior** | O Android Studio **já traz um JDK embutido**, que costuma vir configurado como "Gradle JDK". O código é compilado com compatibilidade Java 17. |
| **Android SDK Platform 37** | `compileSdk = 37` (`targetSdk = 36`, `minSdk = 26`). Se faltar, o Android Studio oferece **instalar automaticamente** (link "Install missing SDK package" ao sincronizar). |
| **Aparelho ou emulador** | **Android 8.0 (API 26) ou superior.** Emulador criado em *Tools → Device Manager* ou celular com *Depuração USB* ativada. |
| **Internet** | Necessária no **primeiro sync do Gradle** (baixa o Gradle 9.8.1 e as dependências) e no **primeiro uso do app** (depois ele funciona offline com o cache — veja a [seção 6](#-6-política-de-cache-e-modo-offline)). |

### 2.2 Passo a passo no Android Studio

1. **Obtenha o código** (clone o repositório ou descompacte o `.zip`).
2. No Android Studio, vá em **File → Open** e selecione a **pasta raiz** `CadernoDeReceitas` (a que contém o arquivo `settings.gradle.kts`).
3. **Aguarde o Gradle Sync** terminar (barra de progresso no canto inferior). Se aparecer o pedido para instalar o *SDK Platform 37*, aceite. O arquivo `local.properties` (caminho do SDK) é criado automaticamente pelo Android Studio e **não é versionado**.
4. Na barra superior, escolha a **configuração de execução**:
   - `app` → o aplicativo completo (**Etapa 2**);
   - `parcial` → o aplicativo da **Parcial** (**Etapa 1**).
5. Escolha o dispositivo (emulador API 26+ ou aparelho conectado) e clique em **Run ▶**.

> 💡 Se o Gradle reclamar de falta de memória, ajuste `org.gradle.jvmargs` em `gradle.properties` (hoje `-Xmx2g`, pensado para máquinas com 8 GB de RAM).

### 2.3 Pela linha de comando

Execute na pasta raiz do projeto. No **Windows** use `gradlew.bat` no lugar de `./gradlew` (ou `.\gradlew.bat` no PowerShell); no **Linux/macOS**, se aparecer "permission denied", rode `chmod +x gradlew` uma vez. Para usar a linha de comando, o Android SDK precisa ser encontrado (o `local.properties` criado pelo Android Studio já resolve; do contrário defina `ANDROID_HOME`) e o `JAVA_HOME` deve apontar para um JDK 17+.

Compilar o APK de debug do app completo (resultado em `app/build/outputs/apk/debug/`):

```bash
./gradlew :app:assembleDebug
```

Instalar no emulador/aparelho conectado:

```bash
./gradlew :app:installDebug
```

Rodar os testes unitários (JVM/Robolectric) de todos os módulos:

```bash
./gradlew test
```

Rodar a análise estática (Android Lint):

```bash
./gradlew lint
```

Para o módulo da Parcial:

```bash
./gradlew :parcial:installDebug
```

No Windows (exemplo):

```bash
gradlew.bat :app:assembleDebug
```

### 2.4 🔑 Não há segredos (nem `.env`)

A API **TheMealDB** oferece a **chave pública de teste `"1"`**, documentada pelo próprio serviço para desenvolvimento e estudo. O projeto usa essa chave por padrão (ela entra apenas na URL base gerada em `BuildConfig.MEALDB_BASE_URL`), então **nada precisa ser configurado** e **nenhum segredo está versionado**.

Quem tiver uma chave própria pode usá-la **sem tocar no repositório**, de uma destas formas:

- no arquivo do seu usuário `~/.gradle/gradle.properties` (no Windows: `C:\Users\<seu-usuario>\.gradle\gradle.properties`):

  ```properties
  MEALDB_API_KEY=sua_chave_aqui
  ```

- ou na linha de comando:

  ```bash
  ./gradlew :app:installDebug -PMEALDB_API_KEY=sua_chave_aqui
  ```

A chave é validada no build (somente letras e números, senão o build falha) e **nunca deve ser versionada**. O `.gitignore` já bloqueia `.env`, `*.env`, `secrets.properties`, `local.properties`, `*.jks`, `*.keystore` e `google-services.json`.

### 2.5 Dicas de uso do app

- **Primeiro uso:** abra com internet. As receitas vistas ficam guardadas e passam a aparecer offline.
- **A busca usa termos em inglês** (a TheMealDB cataloga em inglês): tente `chicken`, `cake`, `pasta`, `beef`... Também há chips de **categorias** para filtrar.
- **Lembrete diário:** na aba *Explorar*, ligue a chave "Lembrete diário". No Android 13+ o app pede a permissão de notificações nesse momento.
- **Testar o deep link manualmente** (com o app instalado e um emulador/aparelho conectado):

  ```bash
  adb shell am start -a android.intent.action.VIEW -d "cadernoreceitas://receita/52772"
  ```

  Isso abre direto a tela de detalhes da receita de id `52772`. IDs que não sejam numéricos (até 12 dígitos) são ignorados.

---

## 📚 3. Bibliotecas externas utilizadas

As versões ficam centralizadas em [`gradle/libs.versions.toml`](gradle/libs.versions.toml) (*version catalog*). Abaixo, **uma linha por biblioteca**, com o papel dela no app.

### 3.1 Ferramentas de build (plugins)

| Plugin | Versão | Para que serve |
|--------|--------|----------------|
| Android Gradle Plugin (`com.android.application`) | 9.4.1 | Compila, empacota e testa os módulos Android (`:app` e `:parcial`). Nenhum plugin `kotlin-android` separado é aplicado nos módulos. |
| Kotlin Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) | 2.4.21 | Habilita o compilador do Jetpack Compose (módulo `:app`). |
| Kotlin Serialization plugin (`org.jetbrains.kotlin.plugin.serialization`) | 2.4.21 | Gera, em tempo de compilação, os serializadores dos DTOs da API e das chaves de navegação (`@Serializable`). |
| Kotlin Parcelize plugin (`org.jetbrains.kotlin.plugin.parcelize`) | 2.4.21 | Gera o código `Parcelable` dos modelos da Parcial, para enviá-los em um `Intent`. |
| KSP (`com.google.devtools.ksp`) | 2.3.12 | Roda o compilador do Room (geração de código dos DAOs/banco e exportação do schema). |

### 3.2 Interface (Jetpack Compose) — `:app`

| Biblioteca | Versão | Para que serve |
|------------|--------|----------------|
| `androidx.compose:compose-bom` | 2026.09.00 | *Bill of Materials*: fixa versões compatíveis entre todas as bibliotecas Compose (por isso elas não têm versão própria). |
| `androidx.compose.ui:ui` | via BOM | Núcleo do Compose: layouts, `Modifier`, semântica de acessibilidade, entrada de toque/teclado. |
| `androidx.compose.ui:ui-graphics` | via BOM | Cores, formas e desenho usados pelo tema e pelos componentes. |
| `androidx.compose.material3:material3` | via BOM | Componentes Material 3 (Scaffold, Card, FilterChip, OutlinedTextField, Snackbar, AlertDialog, Switch...). |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | via BOM | `NavigationSuiteScaffold`: barra de navegação inferior no celular e trilho lateral em telas largas. |
| `androidx.compose.material:material-icons-extended` | via BOM | Conjunto completo de ícones Material (busca, caderno, favorito, compartilhar...). |
| `androidx.compose.ui:ui-tooling-preview` | via BOM | Anotação `@Preview` do Android Studio: `ui/Previews.kt` traz *previews* dos componentes e dos estados (conteúdo, carregando, erro, vazio). |
| `androidx.compose.ui:ui-tooling` | via BOM (somente *debug*) | Ferramentas de inspeção do Android Studio (Layout Inspector) em builds de debug. |
| `androidx.core:core-ktx` | 1.19.1 | Extensões Kotlin da plataforma Android (ex.: `String.toUri()`). |
| `androidx.activity:activity-compose` | 1.13.0 | `setContent`, `enableEdgeToEdge`, `BackHandler` e `rememberLauncherForActivityResult` (Photo Picker e permissão de notificações). |

### 3.3 Ciclo de vida, ViewModel e navegação — `:app`

| Biblioteca | Versão | Para que serve |
|------------|--------|----------------|
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | `collectAsStateWithLifecycle()`: observa o `StateFlow` do ViewModel só enquanto a tela está visível. |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | `viewModel()` nos composables, com fábricas que recebem as dependências manualmente. |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.11.0 | `SavedStateHandle`: guarda busca, filtro e rascunho do formulário para sobreviver à morte do processo. |
| `androidx.navigation3:navigation3-runtime` | 1.2.0 | Núcleo da **Navigation 3**: `NavKey`, back stack (`rememberNavBackStack`) e `entryProvider`. |
| `androidx.navigation3:navigation3-ui` | 1.2.0 | `NavDisplay`: desenha a entrada do topo do back stack. |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 | Decorator que amarra um `ViewModel` a **cada entrada** do back stack (nasce ao empilhar, morre ao desempilhar). |

### 3.4 Persistência local e segundo plano — `:app`

| Biblioteca | Versão | Para que serve |
|------------|--------|----------------|
| `androidx.room:room-runtime` | 2.8.5 | Banco SQLite local: cache das receitas, caderno, anotações, listas e categorias. |
| `androidx.room:room-ktx` | 2.8.5 | Suporte a corrotinas/`Flow` no Room (consultas reativas) e `withTransaction`. |
| `androidx.room:room-compiler` (via KSP) | 2.8.5 | Gera o código dos DAOs e do banco; exporta o schema em `app/schemas/`. |
| `androidx.datastore:datastore-preferences` | 1.2.1 | Preferências do usuário (última busca, lembrete ligado/desligado, receita do dia), com `Flow`. |
| `androidx.work:work-runtime-ktx` | 2.12.0 | **WorkManager** com `CoroutineWorker`: lembrete diário periódico e ação "Salvar no caderno" da notificação. |

### 3.5 Rede, serialização, corrotinas e imagens — `:app`

| Biblioteca | Versão | Para que serve |
|------------|--------|----------------|
| `com.squareup.retrofit2:retrofit` | 3.0.0 | Cliente HTTP declarativo: a interface `MealApi` descreve os endpoints da TheMealDB com funções `suspend`. |
| `com.squareup.retrofit2:converter-kotlinx-serialization` | 3.0.0 | Converte o JSON da API nos DTOs usando o `kotlinx.serialization`. |
| `com.squareup.okhttp3:okhttp` (+ `okhttp-bom`) | 5.5.0 | Motor HTTP por baixo do Retrofit (timeouts de conexão/leitura/chamada, HTTPS). O BOM mantém as versões OkHttp alinhadas. |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | Leitura/escrita de JSON (API e também listas guardadas no cache Room). |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | Corrotinas e `Flow` (inclui o `kotlinx-coroutines-core`) com o dispatcher `Main` do Android. |
| `io.coil-kt.coil3:coil-compose` | 3.6.3 | `AsyncImage`: carrega e exibe as fotos das receitas e a foto do prato do usuário, com cache em memória e disco. |
| `io.coil-kt.coil3:coil-network-okhttp` | 3.6.3 | Backend de rede do Coil 3 sobre o OkHttp (necessário para baixar imagens por HTTPS). |

### 3.6 Interface em Vistas XML — `:parcial`

| Biblioteca | Versão | Para que serve |
|------------|--------|----------------|
| `androidx.core:core-ktx` | 1.19.1 | Extensões Kotlin (ex.: `isVisible`, `updatePadding`, `toUri`). |
| `androidx.appcompat:appcompat` | 1.8.0 | `AppCompatActivity` e compatibilidade de temas/widgets em versões antigas do Android. |
| `com.google.android.material:material` | 1.14.0 | Material Components para Vistas: `MaterialCardView`, `MaterialButtonToggleGroup` e o tema Material 3 DayNight. |
| `androidx.recyclerview:recyclerview` | 1.4.0 | `RecyclerView` + `ListAdapter`/`DiffUtil` para a lista de receitas. |
| `androidx.fragment:fragment-ktx` | 1.9.1 | `Fragment` (lista de ingredientes) e a extensão `commit {}` do `FragmentManager`. |
| `androidx.activity:activity-ktx` | 1.13.0 | `enableEdgeToEdge`, `registerForActivityResult` e o despachante do botão voltar. |

### 3.7 Testes

| Biblioteca | Versão | Para que serve |
|------------|--------|----------------|
| `junit:junit` | 4.13.2 | Framework base dos testes unitários (JVM). |
| `com.google.truth:truth` | 1.4.5 | Asserções legíveis (`assertThat(x).isEqualTo(y)`). |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | `runTest` e *dispatchers* de teste para controlar o tempo virtual em código com corrotinas. |
| `app.cash.turbine:turbine` | 1.2.1 | Testa `Flow`/`StateFlow` coletando os valores emitidos em sequência. |
| `org.robolectric:robolectric` | 4.17 | Executa código Android (Room, WorkManager, Compose, Views) na JVM, sem emulador. |
| `androidx.test:core` | 1.7.0 | Utilitários de teste Android (ex.: `ApplicationProvider` para obter o `Context`). |
| `androidx.test.ext:junit` | 1.3.0 | Integra o JUnit4 ao ambiente Android (`AndroidJUnit4`). |
| `androidx.test:runner` | 1.7.0 | `AndroidJUnitRunner`, para testes instrumentados (`androidTest`). |
| `androidx.room:room-testing` | 2.8.5 | Ferramentas de teste do Room (ex.: `MigrationTestHelper`). |
| `androidx.work:work-testing` | 2.12.0 | Executa e inspeciona Workers e agendamentos do WorkManager em teste. |
| `com.squareup.okhttp3:mockwebserver` | 5.5.0 | Servidor HTTP falso para testar o Retrofit/DTOs sem internet. |
| `androidx.compose.ui:ui-test-junit4` | via BOM | `createComposeRule`: testes de interface Compose. |
| `androidx.compose.ui:ui-test-manifest` | via BOM (*debug*) | Declara a `Activity` de teste necessária aos testes de Compose. |

---

## 📁 4. Estrutura dos módulos e das pastas

```text
CadernoDeReceitas/
├── settings.gradle.kts        # inclui :app e :parcial
├── build.gradle.kts           # declara os plugins (versões no catálogo)
├── gradle/libs.versions.toml  # catálogo de versões/bibliotecas
├── gradle.properties
├── app/                       # ETAPA 2 — Jetpack Compose
│   ├── schemas/               # schema do Room exportado (versão 1)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/               # strings, tema, ícones, network_security_config
│       └── java/com/cadernoreceitas/
│           ├── CadernoApp.kt, MainActivity.kt
│           ├── di/            # AppContainer (injeção manual)
│           ├── domain/model/  # modelos imutáveis do domínio
│           ├── data/
│           │   ├── remote/    # MealApi (Retrofit), DTOs, RecipeRemoteDataSource
│           │   ├── local/     # Room (AppDatabase, DAOs, entidades) e DataStore
│           │   ├── photo/     # PhotoStorage (fotos do prato)
│           │   ├── mapper/    # DTO <-> domínio <-> entidade
│           │   └── repository/# RecipeRepository + DefaultRecipeRepository
│           ├── ui/            # theme, components, navigation, explore, detail, notebook, editnote
│           ├── work/          # WorkManager, notificação e receiver
│           └── util/          # RecipeIds (validação de ids externos)
└── parcial/                   # PARCIAL — Vistas XML + ViewBinding
    └── src/main/
        ├── java/com/cadernoreceitas/parcial/   # model/, data/ (mocks), ui/
        └── res/layout/        # telas e componentes em XML
```

---

## 🧱 5. Arquitetura

### 5.1 Módulo `:app` (Etapa 2)

Camadas com **dependência em um único sentido** (a UI conhece o ViewModel; o ViewModel conhece o Repository; o Repository conhece as fontes de dados):

```text
┌───────────────────────────────────────────────────────────────────────────┐
│ UI — Jetpack Compose                                                      │
│   ExploreScreen · DetailScreen · NotebookScreen · EditNoteScreen          │
│   (Route lê o estado com collectAsStateWithLifecycle e repassa eventos)   │
└───────────────▲───────────────────────────────────────────┬───────────────┘
        UiState (StateFlow)                           eventos (funções)
┌───────────────┴───────────────────────────────────────────▼───────────────┐
│ ViewModel — ExploreVM · DetailVM · NotebookVM · EditNoteVM                │
│   um UiState por tela + SavedStateHandle + corrotinas (viewModelScope)    │
└───────────────────────────────────┬───────────────────────────────────────┘
                                    │ interface RecipeRepository
┌───────────────────────────────────▼───────────────────────────────────────┐
│ Repository — DefaultRecipeRepository (política de cache / offline-first)  │
└──────────────┬──────────────────────────────────────────┬─────────────────┘
               │ fonte REMOTA                              │ fontes LOCAIS
   RecipeRemoteDataSource                  Room (receitas, caderno, cache de listas,
   (Retrofit + OkHttp → TheMealDB)         categorias) · DataStore (preferências)
                                           · arquivos (fotos do prato)
```

Pontos principais:

- **Fluxo unidirecional de dados (UDF):** o estado desce (`StateFlow<UiState>` → `collectAsStateWithLifecycle()`), os eventos sobem (`onQueryChange`, `onCategorySelected`, `toggleSaved`, `onSave`...). Os composables `*Screen` são "burros" (recebem estado e *lambdas*); os `*Route` fazem a ligação com o ViewModel.
- **DTO → domínio → UI:** o JSON da API (`MealDto`, com campos `strIngredient1..20`) é traduzido em `Mappers.kt` para modelos de domínio imutáveis (`Recipe`, `RecipeSummary`, `Category`, `SavedRecipe`); cada ViewModel deriva dele seu `UiState`. O Room usa entidades próprias (`RecipeEntity`, `SavedRecipeEntity`...).
- **Estados visuais explícitos:** `sealed interface` por tela (`ExploreContent`, `DetailContent`, `NotebookContent`) com **Loading / Success / Empty / Error**; as telas trocam entre eles com animação.
- **Erros tipados:** a camada de dados lança `RecipeException(kind = NETWORK | SERVER | INVALID_DATA | NOT_FOUND)`; a UI converte em `UiError` e mostra mensagem amigável (nunca *stack trace*).
- **Injeção manual de dependências:** `AppContainer` (interface) é criado em `CadernoApp` por `DefaultAppContainer` e fornece `RecipeRepository`, `UserPreferencesRepository`, `PhotoStorage` e `ReminderScheduler`. As fábricas de `ViewModel` ficam em `AppNavigation.kt`. Nos testes, `CadernoApp.createContainer()` pode ser sobrescrito para entregar **fakes**.
- **Navigation 3:** o *back stack* é uma lista que o próprio app controla; cada rota é um `NavKey` `@Serializable` (`ExploreKey`, `NotebookKey`, `RecipeDetailKey(recipeId)`, `EditNoteKey(recipeId)`), e o argumento faz parte da chave. Assim o back stack **sobrevive à rotação e à morte do processo**. Os *entry decorators* amarram o estado salvo (`rememberSaveable`) e o `ViewModel` a cada entrada.
- **Activity única:** `MainActivity` hospeda tudo e trata o deep link `cadernoreceitas://receita/{id}`.

### 5.2 Módulo `:parcial` (Parcial)

Arquitetura propositalmente simples (sem rede nem banco): `MockRecipes` (dados simulados) → modelos imutáveis `Recipe`/`Ingredient` → telas com **ViewBinding**:

- **Tela 1 – `RecipeListActivity`:** lista (`RecyclerView` + `RecipeAdapter` com `ListAdapter`/`DiffUtil`), filtros e estado vazio. A lista é uma `List<Recipe>` imutável, substituída por uma nova a cada alteração (`copy`).
- **Tela 2 – `RecipeDetailActivity`:** detalhes da receita, com o `IngredientsFragment` (lista de ingredientes com checkboxes).
- Os dados trafegam entre as telas por **Intent explícito** com extra `Parcelable`, e o resultado (favorito) volta por `setResult`. `Insets.kt` trata o modo *edge-to-edge*.

Detalhes na [seção 8](#-8-rastreabilidade-requisito-do-professor--onde-foi-atendido).

---

## 💾 6. Política de cache e modo offline

A política é definida em `CachePolicy` (arquivo `DefaultRecipeRepository.kt`). Os valores **reais** são:

| Dado | Validade (TTL) | Onde fica |
|------|----------------|-----------|
| Listas (busca por nome, receitas em destaque e receitas por categoria) | **6 horas** | tabela `list_cache` (JSON + data da busca) |
| Detalhe de uma receita | **24 horas** | tabela `recipes` (campo `fetchedAt`) |
| Categorias | **7 dias** | tabela `categories` |
| Limpeza de receitas antigas **não salvas** (e do cache de listas) | **30 dias** | removidas por `purgeExpiredCache()` ao abrir o app |
| "Receita do dia" | **1 dia-calendário** (sorteada uma vez por dia) | DataStore (`daily_recipe_id` + `daily_recipe_epoch_day`) |

**Estratégia de leitura (listas, detalhe e categorias):**

1. **Cache válido (dentro do TTL)** → devolve o cache **sem chamar a rede** (`DataSource.CACHE_FRESH`).
2. Cache vencido ou ausente (ou o usuário tocou em "Tentar novamente", que usa `forceRefresh`) → **busca na API** e atualiza o cache (`DataSource.REMOTE`).
3. **API falhou** (sem internet, servidor fora do ar) → usa o **cache vencido** como alternativa (`DataSource.CACHE_STALE`) e a tela mostra a faixa **"Sem conexão. Mostrando dados salvos no aparelho."** Nas buscas e categorias sem cache de lista, ainda tenta montar o resultado a partir das receitas guardadas em `recipes` (busca `LIKE` com escape dos curingas).
4. Sem cache nenhum e sem rede → lança `RecipeException` e a tela mostra o estado de **erro** com o botão "Tentar novamente".

Observações:

- A busca por nome (`search.php`) já devolve a receita **completa**; por isso ela é gravada em `recipes` e o detalhe abre offline. Já `filter.php` (por categoria) traz só o resumo, então o detalhe é buscado quando o usuário abre a receita.
- **O caderno nunca expira nem é apagado pela limpeza:** a consulta `purgeOlderThan` ignora receitas presentes em `saved_recipes`.
- Fotos de pratos "órfãs" (arquivos sem registro no banco, com mais de 1 hora) também são removidas na manutenção.
- As imagens remotas usam o cache padrão do **Coil** (memória e disco).

---

## 🔄 7. Fluxo completo do app (Etapa 2)

```text
Explorar ──buscar/filtrar──▶ Lista (LazyColumn) ──toque──▶ Detalhes
   │  (debounce 400 ms,         estados: carregando /        │
   │   flatMapLatest cancela    conteúdo / vazio / erro      ├─ Salvar no caderno (marcador) ─▶ Room
   │   a busca anterior)                                      ├─ Compartilhar (ACTION_SEND)
   │                                                          └─ Anotar ─▶ Formulário (nota, observações,
   ▼                                                                       minutos, foto) ─▶ validação ─▶ Room
Meu caderno (100% local, reativo) ◀──────────── Flow do Room atualiza a UI sozinho ───────────┘
   │
   └─ filtros (Todas / Com anotação / 4 estrelas ou mais) · remover com confirmação e "Desfazer"

Persistência: Room (receitas + caderno + anotações) · DataStore (última busca, lembrete) · filesDir (fotos)
              ⇒ tudo continua lá depois de fechar o app, reiniciar o aparelho ou ficar sem internet.
```

Passo a passo:

1. **Consultar o remoto:** `ExploreViewModel` pede ao `RecipeRepository` a lista (Retrofit → TheMealDB). O texto digitado espera 400 ms de pausa (*debounce*), e uma nova busca **cancela** a anterior.
2. **Apresentar:** a lista é exibida em `LazyColumn` (categorias em `LazyRow`), com os estados de carregando, vazio e erro.
3. **Detalhes:** o toque abre `RecipeDetailKey(recipeId)`; `DetailViewModel` carrega a receita (cache/API) e observa se ela está no caderno.
4. **Registrar/alterar informação local:** o ícone de marcador salva/remove a receita (com "Desfazer"); o botão **Anotar** abre `EditNoteKey(recipeId)`, formulário com **validação** (nota obrigatória de 1 a 5; observações obrigatórias de 10 a 500 caracteres; tempo de preparo opcional de 1 a 600 minutos; foto opcional pelo Photo Picker). Os erros aparecem ao sair do campo ou ao tentar salvar.
5. **Persistir:** a gravação ocorre em **transação** no Room (`saveNote` salva a receita, cria o registro do caderno e atualiza a anotação). A foto é reduzida (lado maior ≤ 1600 px, JPEG 85) e movida para a pasta interna do app.
6. **Manter após reiniciar:** o caderno é lido do Room por `Flow`; ao reabrir o app, a aba *Meu caderno* mostra tudo de novo, mesmo offline. A última busca é restaurada pelo DataStore.

---

## ✅ 8. Rastreabilidade: requisito do professor → onde foi atendido

Caminhos relativos à raiz do projeto. `…/` abrevia `app/src/main/java/com/cadernoreceitas/`.

### 8.1 Parcial — obrigatórios

| Requisito do professor | Onde foi atendido |
|------------------------|-------------------|
| **2 telas em Vistas XML** usando `TextView`, `ImageView`, `Space`, `LinearLayout`, `FrameLayout` e `RecyclerView` | Tela 1: `parcial/src/main/res/layout/activity_recipe_list.xml` (`LinearLayout`, `FrameLayout`, `RecyclerView`, `ImageView`, `TextView`, `Space`). Tela 2: `parcial/src/main/res/layout/activity_recipe_detail.xml` (`ScrollView`, `LinearLayout`, `FrameLayout`, `ImageView`, `TextView`, `Space`). Itens: `item_recipe.xml`, `item_step.xml`, `item_ingredient.xml`, `fragment_ingredients.xml`. |
| **Navegação por Intent explícito com passagem de dados** | Tela 1 (`parcial/.../ui/RecipeListActivity.kt`, método `openDetail`): `Intent(this, RecipeDetailActivity::class.java).putExtra(EXTRA_RECIPE, recipe)`, aberto com `registerForActivityResult`. Tela 2 (`parcial/.../ui/RecipeDetailActivity.kt`): lê a `Recipe` do extra `EXTRA_RECIPE` (`IntentCompat.getParcelableExtra`) e devolve `EXTRA_RESULT_ID`/`EXTRA_RESULT_FAVORITE` por `setResult`; a tela 1 usa o resultado para atualizar a lista. Se o extra vier nulo, mostra aviso e encerra a tela. |
| **ViewBinding com ao menos uma interação que atualiza a UI** | `viewBinding = true` em `parcial/build.gradle.kts`. Interações que atualizam a UI: estrela de favoritar (`RecipeAdapter.bind`, `RecipeListActivity.toggleFavorite` e `RecipeDetailActivity.toggleFavorite`/`updateFavoriteButton`), filtros Todas/Rápidas/Favoritas (`MaterialButtonToggleGroup` em `RecipeListActivity.setupFilters`, que redesenha lista, contador e estado vazio em `render()`) e marcação de ingredientes com contador "x de y separados" (`IngredientsFragment.updateProgress`). |
| **Modelos imutáveis (`data class`), opcionais tratados e dados simulados** | `parcial/.../model/Recipe.kt`: `Recipe` e `Ingredient` são `data class` só com `val` (alterações com `copy`, ex.: favoritar); opcionais com tipos anuláveis (`Ingredient.quantity`, `Recipe.tip`, `Recipe.videoUrl`). Dados simulados: `parcial/.../data/MockRecipes.kt` (8 receitas, algumas com campos nulos de propósito). Tratamento na UI: `RecipeDetailActivity.bindRecipe` (esconde o card da dica e o botão de vídeo quando nulos) e `IngredientsFragment.ingredientText` (ingrediente sem quantidade). |

### 8.2 Parcial — opcionais

| Requisito do professor | Onde foi atendido |
|------------------------|-------------------|
| **Somente ViewBinding** (sem `findViewById`) | Todas as telas e itens usam classes geradas (`ActivityRecipeDetailBinding`, `ItemRecipeBinding`, `FragmentIngredientsBinding`, `ItemStepBinding`, `ItemIngredientBinding`, `ViewInfoChipBinding`...). |
| **Reutilizar componentes XML / inflar** | `view_info_chip.xml` reutilizado 3× por `<include>` em `activity_recipe_detail.xml` (tempo, porções, dificuldade); `item_step.xml` e `item_ingredient.xml` inflados em laço (`RecipeDetailActivity.bindRecipe`, `IngredientsFragment.onViewCreated`); `item_recipe.xml` inflado por `RecipeAdapter`. |
| **Fragment com ciclo de vida e ViewBinding** | `parcial/.../ui/IngredientsFragment.kt`: `_binding` criado em `onCreateView` e zerado em `onDestroyView`; recebe dados por `arguments`; estado dos checkboxes salvo em `onSaveInstanceState`; hospedado por `FragmentContainerView` e `supportFragmentManager.commit`. |

### 8.3 Etapa 2 — obrigatórios

| Requisito do professor | Onde foi atendido |
|------------------------|-------------------|
| **Telas e componentes coerentes com o contexto** | `…/ui/explore/ExploreScreen.kt`, `…/ui/detail/DetailScreen.kt`, `…/ui/notebook/NotebookScreen.kt`, `…/ui/editnote/EditNoteScreen.kt`; componentes `RecipeCard`, `RatingInput`, `RatingDisplay`, `RecipeImage` em `…/ui/components/RecipeComponents.kt`; tema próprio em `…/ui/theme/Theme.kt`. |
| **Acessibilidade** (contraste, descrição de elementos, áreas de toque, texto maior) | Ver [seção 10](#-10-acessibilidade). Arquivos: `…/ui/theme/Theme.kt`, `…/ui/components/RecipeComponents.kt`, `…/ui/components/StateViews.kt`, `…/ui/editnote/EditNoteScreen.kt`, `…/ui/detail/DetailScreen.kt`, textos em `app/src/main/res/values/strings.xml`. |
| **Navigation 3** (rotas, entradas, back stack, argumentos) | `…/ui/navigation/AppNavigation.kt`: rotas `ExploreKey`, `NotebookKey`, `RecipeDetailKey(recipeId)`, `EditNoteKey(recipeId)` (`NavKey` + `@Serializable`); `rememberNavBackStack`; `NavDisplay`; `entryProvider { entry<...> }`; `entryDecorators`; `BackHandler`. Deep link tratado em `…/MainActivity.kt`. |
| **Pelo menos uma lista `LazyColumn`/`LazyRow`** | `LazyColumn` em `ExploreScreen.kt` (`RecipeList`) e em `NotebookScreen.kt` (caderno); `LazyRow` para os chips de categorias (`ExploreScreen.kt`, `CategoryChips`) e filtros (`NotebookScreen.kt`, `FilterRow`). |
| **Formulário com validação e feedback** | `…/ui/editnote/EditNoteScreen.kt` (campos, `isError`, `supportingText`, contador, `AlertDialog` ao descartar), `EditNoteViewModel.kt` (erros só após tocar no campo ou tentar salvar) e `NoteValidator.kt` (regras puras: nota 1–5, observações 10–500, minutos 1–600). |
| **ViewModel + UiState/`StateFlow` + `collectAsStateWithLifecycle` + fluxo unidirecional** | `ExploreViewModel`, `DetailViewModel`, `NotebookViewModel`, `EditNoteViewModel` (cada um expõe `uiState: StateFlow<...>`); os `*Route` em cada `*Screen.kt` usam `collectAsStateWithLifecycle()` e passam eventos de volta ao ViewModel. |
| **Estados visuais distintos** (carregando / conteúdo / lista vazia / erro) **preservando estado relevante após rotação/recriação** | Estados: `sealed interface ExploreContent`/`DetailContent`/`NotebookContent`; componentes `LoadingState`, `EmptyState`, `ErrorState`, `OfflineBanner` em `…/ui/components/StateViews.kt`. Preservação: `SavedStateHandle` (busca, categoria, filtro do caderno, rascunho do formulário), `rememberSaveable` (ingredientes marcados, texto expandido, diálogos), `NavBackStack` serializável, `ViewModel` por entrada do back stack. |
| **Repository** | `…/data/repository/RecipeRepository.kt` (interface) e `DefaultRecipeRepository.kt` (implementação: API + Room + DataStore + política de cache). |
| **Operação assíncrona com corrotinas, escopos apropriados e cancelamento** | `viewModelScope` + `debounce` + **`flatMapLatest`** (cancela a busca anterior e a chamada HTTP) em `ExploreViewModel`; `CancellationException` sempre repassada em `RecipeRemoteDataSource.kt` e nos ViewModels; cancelamento da importação de foto (`photoJob?.cancel()` em `EditNoteViewModel`, `ensureActive()` em `PhotoStorage.kt`); `SharingStarted.WhileSubscribed(5_000)`; escopo de processo com `SupervisorJob` em `CadernoApp.kt`; `CoroutineWorker` em `…/work/Workers.kt`. |
| **Persistência local (Room/DataStore/arquivos) com exposição reativa** | Room: `…/data/local/AppDatabase.kt`, `dao/Daos.kt` (consultas que retornam `Flow`), `entity/Entities.kt`; DataStore: `…/data/local/prefs/UserPreferencesRepository.kt` (`Flow<UserPreferences>`); arquivos: `…/data/photo/PhotoStorage.kt`. |
| **Nada de segredos versionados** | `app/build.gradle.kts` (chave pública de teste `"1"` por padrão; `MEALDB_API_KEY` opcional fora do repositório) e `.gitignore` (`.env`, `*.env`, `secrets.properties`, `local.properties`, `*.jks`, `*.keystore`, `google-services.json`). |
| **Fluxo completo:** consultar remoto → apresentar → detalhes → registrar/alterar informação local → manter após reiniciar | [Seção 7](#-7-fluxo-completo-do-app-etapa-2): `ExploreViewModel` → `ExploreScreen` → `DetailScreen` → `EditNoteScreen`/`saveNote` → Room → `NotebookScreen` (lê do Room mesmo após reiniciar). |

### 8.4 Etapa 2 — opcionais

| Requisito do professor | Onde foi atendido |
|------------------------|-------------------|
| **DTO → domínio → UI** | DTOs: `…/data/remote/dto/MealDto.kt`; mapeamentos: `…/data/mapper/Mappers.kt`; domínio: `…/domain/model/Models.kt`; UiState em cada ViewModel. |
| **Testes automatizados** | Ver [seção 12](#-12-testes). Módulo `:app`: `app/src/test/java/com/cadernoreceitas/` (mapeadores, DTOs, política de cache do repositório, ViewModels, validador do formulário, contraste das cores, ids/deep link e lógica do WorkManager), com fakes em `testutil/` e `uitesting/`. Módulo `:parcial`: classes em `parcial/src/test/java/com/cadernoreceitas/parcial/` (`MockRecipesTest`, `RecipeFilterTest`, `RecipeParcelTest`, `RecipeListActivityTest`, `RecipeDetailActivityTest`). |
| **Boas práticas de código seguro** (validar entradas, sem dados sensíveis no Logcat) | Ver [seção 11](#-11-segurança): `NoteValidator.kt` (sanitização), `DefaultRecipeRepository.normalizeQuery`/`escapeLike`, `util/RecipeIds.kt` (deep link/notificação), `res/xml/network_security_config.xml` (somente HTTPS), `PhotoStorage.requireInside`. O código não faz nenhuma chamada a `Log` nem a `println`. |
| **Cache remoto com política de validade** | `CachePolicy` e `cachedList(...)` em `…/data/repository/DefaultRecipeRepository.kt`; tabela `list_cache` em `Entities.kt` (ver [seção 6](#-6-política-de-cache-e-modo-offline)). |
| **Offline-first** | `DefaultRecipeRepository` (cache → rede → cache vencido), caderno 100% local em `SavedRecipeDao`, `DataSource.CACHE_STALE` e `OfflineBanner` (`StateViews.kt`). |
| **Animações implícitas** | `AnimatedContent`/`AnimatedVisibility` (`ExploreScreen.kt`, `NotebookScreen.kt`), `animateContentSize` e `animateColorAsState` (`DetailScreen.kt`), `animateItem()` na lista do caderno. |
| **Adaptação a tamanhos de tela** | `NavigationSuiteScaffold` com `currentWindowAdaptiveInfoV2` em `AppNavigation.kt` (barra inferior ↔ trilho lateral); `BoxWithConstraints` com **duas colunas a partir de 720 dp** em `DetailScreen.kt`; `widthIn(max = 720.dp)` em Explorar/Caderno e `600.dp` no formulário; edge-to-edge. |
| **Injeção manual de dependências + interfaces de Repository + fakes** | `…/di/AppContainer.kt`; interfaces `RecipeRepository`, `RecipeRemoteDataSource`, `UserPreferencesRepository`, `PhotoStorage`, `ReminderScheduler`; `CadernoApp` é `open` com `createContainer()` sobrescrevível para testes. As implementações falsas ficam em `app/src/test` (ver [seção 12](#-12-testes)). |
| **WorkManager** | `…/work/Workers.kt`: `DailyRecipeWorker` (periódico de 24 h, primeira execução no próximo 09:00, exige rede, *backoff* exponencial) e `SaveRecipeWorker` (trabalho único `save_recipe_<id>`); agendamento por `WorkManagerReminderScheduler`; disparo da ação em `…/work/SaveRecipeReceiver.kt`. |
| **Integração com recursos do celular** (Photo Picker, câmera ou compartilhamento; deep link; notificação com ação) | **Photo Picker** (`PickVisualMedia`, só imagens, sem permissão de armazenamento) em `EditNoteScreen.kt`; **compartilhamento** (`ACTION_SEND` + seletor do sistema) e abertura do vídeo (`ACTION_VIEW`) em `DetailScreen.kt`; **deep link** `cadernoreceitas://receita/{id}` em `AndroidManifest.xml`, `MainActivity.kt` e `util/RecipeIds.kt`; **notificação com ação "Salvar no caderno"** em `…/work/NotificationHelper.kt` + `SaveRecipeReceiver.kt`; permissão `POST_NOTIFICATIONS` pedida em tempo de execução em `ExploreScreen.kt`. (Câmera: não implementada.) |

---

## 🧠 9. Decisões técnicas

- **Room para receitas e caderno.** São dados relacionais e consultáveis (busca por nome, filtro por categoria, ordenação), que precisam de transações e de **consultas reativas** (`Flow`): quando algo é salvo, anotado ou removido em uma tela, a outra se atualiza sozinha.
- **DataStore para preferências.** São poucos pares chave-valor (última busca, lembrete ligado, receita do dia). O DataStore substitui o `SharedPreferences` com API assíncrona, sem bloquear a thread principal, e expõe `Flow`. Se o arquivo estiver ilegível, o app cai nos valores padrão em vez de travar.
- **Arquivos para a foto do prato.** Imagem é dado binário: o banco guarda só o **caminho**; o arquivo fica em `filesDir/photos`. O fluxo é *importar para cache temporário → confirmar ao salvar a anotação → apagar a foto antiga só depois da gravação*, evitando perder a foto se algo falhar no meio.
- **Offline-first.** A fonte da verdade na tela é o dado local: dentro do TTL não há chamada de rede; sem rede, o app mostra o último dado conhecido com um aviso explícito (`DataSource` acompanha o valor em `Loaded<T>`, e a UI não precisa adivinhar de onde ele veio).
- **`@Upsert` e nenhuma `FOREIGN KEY ... ON DELETE CASCADE` entre `recipes` e `saved_recipes`.** Com `OnConflictStrategy.REPLACE`, o SQLite **apaga e reinsere** a linha; se houvesse chave estrangeira com `CASCADE`, atualizar o cache de uma receita **apagaria a anotação do usuário**. Por isso: (1) `@Upsert` atualiza a linha no lugar; (2) não há `CASCADE` — o cache (`recipes`) pode ser renovado ou limpo sem tocar no caderno; (3) a limpeza (`purgeOlderThan`) exclui explicitamente receitas que estão em `saved_recipes`.
- **Salvar é idempotente.** `insertIfAbsent` (`OnConflictStrategy.IGNORE`) garante que salvar duas vezes a mesma receita (toque duplo, ou a ação da notificação repetida) não duplica nem sobrescreve a anotação; a anotação é gravada por um `UPDATE` separado, tudo dentro de `withTransaction`.
- **`flatMapLatest` + `debounce` na busca.** Cada digitação gera uma nova consulta; `flatMapLatest` **cancela** a anterior (e a chamada HTTP junto, pois as funções `suspend` do Retrofit são canceláveis), evitando respostas atrasadas sobrescrevendo as novas. O `debounce` de 400 ms evita uma requisição por letra.
- **`stateIn(WhileSubscribed(5_000))`.** O estado é compartilhado e sobrevive a rotações (a janela de 5 s cobre a recriação da Activity), mas o fluxo upstream para quando o app vai para segundo plano.
- **`SavedStateHandle` para o estado "do usuário".** Texto da busca, categoria, filtro do caderno e rascunho do formulário sobrevivem também à **morte do processo**; dados carregados da rede/banco são simplesmente recarregados.
- **WorkManager.** O lembrete diário precisa continuar agendado depois que o app fecha e que o aparelho reinicia, respeitar condição de rede e repetir com *backoff* em falhas transitórias. A ação "Salvar no caderno" da notificação roda em um `BroadcastReceiver` (que vive poucos segundos), por isso ele **só valida o id e enfileira** um `SaveRecipeWorker` (nome único + `KEEP`, então toques repetidos geram um único salvamento).
- **Injeção manual (`AppContainer`) em vez de Hilt/Koin.** Para o tamanho do projeto, deixa o grafo de dependências explícito e simples de ler, sem geração de código extra, e ainda permite trocar tudo por fakes nos testes.
- **Navigation 3 com `NavKey` serializável.** O back stack é uma lista observável sob controle do app; o argumento (`recipeId`) vive na própria chave, e a serialização restaura a pilha após rotação/morte do processo.
- **Retrofit + kotlinx.serialization com `ignoreUnknownKeys` e `coerceInputValues`.** A API pode ganhar campos sem quebrar o app. As esquisitices da TheMealDB (`strIngredient1..20`, mistura de `null` e `""`) ficam confinadas em `MealDto` e `Mappers.kt`; o resto do app só vê modelos limpos.
- **Interfaces nas bordas** (`RecipeRepository`, `RecipeRemoteDataSource`, `UserPreferencesRepository`, `PhotoStorage`, `ReminderScheduler`): permitem testes sem rede, sem banco real e sem WorkManager real.
- **`allowBackup="false"`.** Anotações e fotos não são copiadas para o backup automático do Android (consequência: elas não são restauradas ao trocar de aparelho).

---

## ♿ 10. Acessibilidade

Medidas presentes no código do `:app`:

- **Contraste:** a paleta terracota (`Theme.kt`) foi desenhada para texto sobre fundo com contraste mínimo de 4,5:1 (WCAG AA). O teste automatizado `ColorContrastTest` calcula a razão de contraste WCAG de **todos os pares texto/fundo** e exige no mínimo 4,5:1 (texto) e 3:1 (contornos), nos temas claro e escuro; ele passa. Há tema **claro e escuro** (segue o sistema).
- **Descrição de elementos:** ícones interativos têm `contentDescription` traduzida (voltar, compartilhar, limpar busca, "Remover *X* do caderno"); ícones decorativos usam `contentDescription = null`; a foto do prato tem descrição própria.
- **Semântica para leitores de tela:** títulos marcados com `heading()`; cartões de receita mesclados em um único item (`mergeDescendants`) com `stateDescription` "Salva no caderno"; estrelas de nota como grupo de *radio buttons* ("3 estrelas"); linhas com `toggleable` + `Role.Checkbox`/`Role.Switch` (a linha inteira é o alvo e o leitor anuncia ligado/desligado); mensagens de carregamento/erro e erros de campo como *live regions* (`LiveRegionMode.Polite`).
- **Áreas de toque ≥ 48 dp:** estrelas de nota (`size(48.dp)`), linhas de ingredientes/lembrete e botões do detalhe e do formulário (`heightIn(min = 48.dp)`); `IconButton`s e chips seguem o tamanho mínimo interativo de 48 dp do Material 3.
- **Texto maior / fonte do sistema:** textos em `sp` pela tipografia do Material 3 (acompanham a fonte do sistema); os estados de erro/vazio rolam na vertical, e o formulário rola e usa `imePadding()`, continuando utilizáveis com fonte grande ou tela pequena.
- **Erros que não dependem só de cor:** além de pintar o campo de vermelho, há texto de ajuda/erro, contador de caracteres e mensagem "Corrija os campos destacados".
- **Navegação previsível:** botão voltar do sistema tratado (inclusive aviso antes de descartar alterações do formulário).

No módulo `:parcial`: `contentDescription` nos botões de ícone, `accessibilityHeading`, regiões *live* (`accessibilityLiveRegion`) para contadores, alvos de toque de 48 dp (`@dimen/min_touch_target`) e tema Material 3 DayNight (claro/escuro).

---

## 🔒 11. Segurança

- **Sem segredos no repositório:** chave pública de teste por padrão; chave própria só via `~/.gradle/gradle.properties` ou `-P`, validada por regex; `.gitignore` cobre `.env`, keystores e `local.properties`.
- **Somente HTTPS:** `network_security_config.xml` com `cleartextTrafficPermitted="false"`; no mapeamento, URLs de imagem/vídeo `http://` são promovidas a `https://` e qualquer outro esquema é descartado.
- **Validação de entrada:**
  - busca: remove caracteres de controle, normaliza espaços e limita a 60 caracteres; curingas do `LIKE` (`%`, `_`) são escapados;
  - formulário: observações (10–500), minutos (somente dígitos, 1–600) e nota (1–5), com sanitização na digitação;
  - **deep link e notificação:** o id da receita só é aceito se for numérico (`^[0-9]{1,12}$`, `RecipeIds`); host e esquema também são verificados.
- **Componentes não exportados:** o `SaveRecipeReceiver` tem `exported="false"`; as `PendingIntent`s usam `FLAG_IMMUTABLE`. A única `Activity` exportada é a `MainActivity` (launcher + deep link validado).
- **Arquivos:** `PhotoStorage` confere que o caminho está dentro da pasta permitida (`requireInside`, contra `../`), aceita só tipos `image/*` e reduz a imagem (limita o uso de memória).
- **Sem dados sensíveis no Logcat:** o código não usa `Log`/`println` e o cliente HTTP não tem *interceptor* de logging.
- **Erros sem vazamento de detalhes:** a UI mostra mensagens traduzidas (`UiError`), sem exceções nem *stack traces*.
- **Sem backup automático** (`allowBackup="false"`) dos dados locais.
- **Permissão mínima:** apenas `INTERNET` e `POST_NOTIFICATIONS` (esta pedida só quando o usuário liga o lembrete). O Photo Picker dispensa permissão de armazenamento.

---

## 🧪 12. Testes

Os testes são **unitários, executados na JVM** (sem emulador), com **Robolectric** quando precisam de classes do Android. Para rodá-los:

```bash
./gradlew test
```

Para rodar só um módulo:

```bash
./gradlew :parcial:testDebugUnitTest
```

```bash
./gradlew :app:testDebugUnitTest
```

Os relatórios HTML são gerados em `<módulo>/build/reports/tests/`.

### 12.1 Módulo `:parcial` — `parcial/src/test/java/com/cadernoreceitas/parcial/`

| Classe de teste | O que verifica |
|-----------------|----------------|
| `data/MockRecipesTest` | Dados simulados: ids únicos, `byId`, receitas com dica/vídeo/quantidade nulos e preenchidos, ingredientes e passos presentes, divisão pelos filtros. |
| `model/RecipeFilterTest` | Regra de receita "rápida" (até 30 min), os três filtros (`ALL`, `QUICK`, `FAVORITES`), imutabilidade (`copy` não altera a original) e valores padrão nulos dos opcionais. |
| `model/RecipeParcelTest` (Robolectric) | Ida e volta das receitas por `Parcel` (o que viaja no `Intent`), inclusive com valores nulos e favorito. |
| `ui/RecipeListActivityTest` (Robolectric) | Tela 1: lista e contador, conteúdo dos itens, filtros, estado vazio, favoritar pela estrela, abertura da tela 2 por Intent explícito com a receita, uso do resultado devolvido e preservação do filtro/favoritos após recriar a Activity. |
| `ui/RecipeDetailActivityTest` (Robolectric) | Tela 2: preenchimento dos campos e chips, ocultação do card de dica e do botão de vídeo quando nulos, favoritar e resultado, `IngredientsFragment` (lista e contagem dos separados), sobrevivência à recriação, ausência de extra (aviso e fim da tela) e botão voltar. |

### 12.2 Módulo `:app` — `app/src/test/java/com/cadernoreceitas/`

| Classe de teste | O que verifica |
|-----------------|----------------|
| `data/mapper/MappersTest` | DTO → domínio (instruções com CRLF, ingredientes das 20 posições, `strArea`/`strCountry`, opcionais vazios → `null`, `http` → `https`, esquemas perigosos descartados), domínio ↔ entidade Room e JSON de ingredientes/cache corrompido ou com chaves desconhecidas. |
| `data/remote/dto/DtoDeserializationTest` | Desserialização das respostas reais da API (fixtures), `meals: null`, campos novos ignorados e JSON inválido/HTML falhando com `SerializationException`. |
| `data/repository/DefaultRecipeRepositoryTest` (Robolectric, Room em memória) | Política de cache: limites exatos dos TTLs (6 h / 24 h...), `forceRefresh`, origem `REMOTE`/`CACHE_FRESH`/`CACHE_STALE`, modo offline, fallback local (inclusive escape do `LIKE`), erros tipados (`NOT_FOUND`, rede, servidor) e receita do dia. |
| `data/repository/RecipeQueryUtilsTest` | Normalização do texto de busca (espaços, controles, limite de 60 caracteres) e escape dos curingas do `LIKE`. |
| `domain/model/ModelsTest` | Regras dos modelos (`Loaded.isOffline`, `hasNote`, `toSummary`), `RecipeException` → `UiError` e propagação do cancelamento. |
| `ui/explore/ExploreViewModelTest` | `debounce`, cancelamento da busca anterior (`flatMapLatest`), categoria × texto, estados Empty/Error/offline, restauração da última busca (DataStore e `SavedStateHandle`), lembrete diário com/sem permissão, receita do dia e categorias em paralelo. |
| `ui/detail/DetailViewModelTest` | Loading/Success/Error, aviso offline, salvar/remover/**desfazer**, reação a mudanças feitas em outras telas e falhas de gravação. |
| `ui/editnote/EditNoteViewModelTest` (Robolectric) | Formulário: erros só após o campo perder o foco, salvar válido/inválido, `isDirty`, restauração pelo `SavedStateHandle`, fluxo da foto (importação, cancelamento da anterior, troca, remoção, confirmação só ao gravar). |
| `ui/editnote/NoteValidatorTest` | Limites das regras (nota 1–5, observações 10–500, minutos 1–600) e funções de sanitização. |
| `ui/notebook/NotebookViewModelTest` | Filtros do caderno, estados Loading/Success/Empty/Error, remover/desfazer, filtro no `SavedStateHandle` e `retry` após erro do `Flow`. |
| `ui/theme/ColorContrastTest` | Contraste mínimo de 4,5:1 (texto) e 3:1 (contornos) nos temas claro e escuro. |
| `util/RecipeIdsTest` | Validação de ids e do deep link (injeção, *path traversal*, esquema/host errados). |
| `work/WorkLogicTest` | Cálculo do atraso até as 9 h e política de nova tentativa do Worker (rede/servidor × erros definitivos). |
| `data/remote/RetrofitRecipeRemoteDataSourceTest` | Retrofit + `MockWebServer`: sucesso, `meals: null`, HTTP 500, corpo inválido, desconexão e **cancelamento não convertido em erro**. |
| `data/local/prefs/DataStoreUserPreferencesRepositoryTest` | Valores padrão e persistência da última busca, categoria, lembrete e receita do dia no DataStore. |
| `data/photo/FilePhotoStorageTest` (Robolectric) | Proteção contra *path traversal*, `commit`/`delete`, limpeza de fotos órfãs respeitando a idade mínima e cálculo da redução da imagem. |
| `work/DailyRecipeWorkerTest`, `SaveRecipeWorkerTest`, `SaveRecipeReceiverTest`, `NotificationHelperTest`, `WorkManagerReminderSchedulerTest`, `CadernoAppStartupTest` | Workers executados de verdade (`TestListenableWorkerBuilder`): sucesso, nova tentativa e falha; notificação com ação "Salvar no caderno" e deep link no toque; receiver (id inválido/ação errada ignorados; trabalho único); agendamento periódico e inicialização do app. |
| `ui/components/*Test`, `ui/explore/ExploreScreenTest`, `ui/detail/DetailScreenTest`, `ui/notebook/NotebookScreenTest`, `ui/editnote/EditNoteScreenTest` (Compose + Robolectric) | Os **4 estados visuais** de cada tela, semântica de acessibilidade (descrições, estado "Salva no caderno", estrelas como *radio buttons*), mensagens de validação do formulário, diálogos de confirmação, callbacks e **fonte 2× maior** com alvos de toque ≥ 48 dp. |
| `app/AppSmokeTest` (Robolectric, **ponta a ponta**) | Abre a `MainActivity` real com a Navigation 3: Explorar → detalhes → salvar → aba Meu caderno → anotar (formulário inválido e válido) → remover/desfazer; busca com *debounce*, erro + "Tentar novamente", faixa offline, lembrete; **recriação da Activity (rotação)** em detalhes, busca, formulário e caderno; **deep link** válido, inválidos (id não numérico, host/esquema errados, caminho extra) e com o app já aberto. |

Apoio aos testes: fixtures JSON em `app/src/test/resources/fixtures/` (respostas reais da API, reduzidas) e dois pacotes de fakes/utilitários: `testutil/` (usado pelos testes de dados e de *workers*: `FakeRecipeRemoteDataSource`, `FakeUserPreferencesRepository`, `FakePhotoStorage`, `FakeAppContainer`, `MutableClock` para controlar o TTL, `RepositoryTestEnv`, `TestFixtures`) e `uitesting/` (usado pelos testes de ViewModel, de telas e ponta a ponta: `FakeRecipeRepository`, `FakeReminderScheduler`, `MainDispatcherRule`, helpers de Turbine e de Compose, `SampleData`). Em cada pacote, `TestCadernoApp` é uma subclasse de `CadernoApp` que entrega um container falso aos testes Robolectric.

**Resultado da última execução completa** (feita em um clone limpo do repositório, sem cache do Gradle): **`:app` = 598 testes e `:parcial` = 34 testes (632 no total), 0 falhas, 0 ignorados.**

**Testes instrumentados:** `app/src/androidTest/` contém testes de tela e de fluxo para rodar em emulador/aparelho (`./gradlew :app:connectedDebugAndroidTest`). Eles **compilam** (`:app:compileDebugAndroidTestKotlin`), mas **não foram executados** na máquina de desenvolvimento por falta de emulador; o mesmo fluxo é coberto, na JVM, por `app/AppSmokeTest`.

---

## 🚧 13. Limitações conhecidas

- **Sem validação em emulador/aparelho físico na máquina de desenvolvimento** (faltou espaço em disco e virtualização). O projeto foi validado por vários métodos independentes: (1) **build do zero em um clone limpo** do repositório (`assembleDebug` dos dois módulos e builds de *release*), (2) **testes na JVM/Robolectric** (inclusive um teste ponta a ponta que abre a `MainActivity` real), (3) **Android Lint** (0 erros), (4) **inspeção do APK** gerado (pacote, permissões, componentes exportados, ícones) e (5) uma **chamada real à API TheMealDB** usando o mesmo Retrofit, DTOs e mapeadores do app (25 receitas em destaque, busca, categorias, detalhe e receita aleatória, todos mapeados sem perdas). Mesmo assim, recomenda-se rodar o app em um emulador/aparelho (API 26+) antes de uma demonstração.
- **Sincronização de alterações locais para uma fonte remota não se aplica:** a TheMealDB é uma API **somente leitura** (não aceita anotações, notas nem favoritos). Por isso o **WorkManager é usado para o lembrete diário e para a ação "Salvar no caderno" da notificação**, e não para enviar dados a um servidor.
- **A busca depende do idioma da API:** termos em **inglês** (`chicken`, `cake`...), e os nomes e instruções das receitas vêm em inglês; a interface do app está apenas em português (pt-BR).
- **Serviço de terceiros com chave pública de teste:** a disponibilidade e eventuais limites da chave `"1"` são do provedor; sem internet no primeiro uso não há nada em cache para mostrar.
- **Lembrete diário aproximado:** o WorkManager não garante hora exata; o trabalho é agendado para o próximo 09:00, mas o Android pode atrasar a execução (economia de bateria, Doze) e ele só roda com internet. No Android 13+ depende da permissão de notificações.
- **Sem câmera nem login/nuvem:** a foto do prato vem do Photo Picker; não há contas de usuário nem sincronização entre aparelhos.
- **Banco na versão 1:** o schema é exportado em `app/schemas/`, mas ainda não houve migrações.
- **Build de release sem minificação** (`isMinifyEnabled = false`) e sem assinatura de release configurada: o projeto é entregue para execução em debug.
- **Parcial:** dados 100% simulados e em memória: o filtro e os favoritos sobrevivem à rotação da tela (`onSaveInstanceState`), mas **não são persistidos** ao fechar o app.

---

## 🙏 14. Créditos e fonte dos dados

- Receitas, imagens e categorias: [**TheMealDB**](https://www.themealdb.com/) (API pública; chave de teste `1`). O app é um projeto acadêmico e não tem vínculo com o serviço.
- Ícones: Material Icons (biblioteca `material-icons-extended`) e ícones vetoriais próprios nos recursos (`res/drawable`).
