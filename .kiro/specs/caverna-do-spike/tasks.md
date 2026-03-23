# Plano de Implementação — Spike na Caverna

## Visão Geral

Implementação incremental do jogo Android "Spike na Caverna" em Kotlin puro, organizada por dependência técnica: fundação do projeto → núcleo do jogo → subsistemas → integração final. Cada tarefa referencia os requisitos e propriedades de correção que valida.

## Tarefas

- [ ] 1. Configurar estrutura do projeto e dependências
  - Adicionar dependências no `build.gradle.kts`: Room, kotlinx.serialization, Kotest, In-App Updates API, WorkManager
  - Criar estrutura de pacotes em `com.ericleber.joguinho/`: `core/`, `renderer/`, `pcg/`, `input/`, `character/`, `persistence/`, `audio/`, `social/`, `update/`, `biome/`, `ui/`
  - Configurar `backup_rules.xml` para incluir banco Room e excluir logs internos
  - Criar `Logger.kt` em `core/` com método `error(tag, message, throwable)` que grava em arquivo de log interno
  - _Requisitos: 7.5, 19.5, 23.5_

- [ ] 2. Implementar modelos de dados e serialização
  - [ ] 2.1 Criar data classes de domínio: `Position`, `MazeData`, `HeroState`, `SpikeState`, `MonsterState`, `TrapState`, `PlayerStatistics`
    - Anotar com `@Serializable` onde aplicável
    - _Requisitos: 7.1, 24.6_
  - [ ] 2.2 Criar `SaveState.kt` com todos os campos do esquema completo, incluindo `activeCharacterId` e `activeSkinId` com valores padrão
    - _Requisitos: 7.1, 24.6, 24.8_
  - [ ]* 2.3 Escrever teste de propriedade para round-trip de SaveState
    - **Propriedade 4: Round-trip de SaveState**
    - **Valida: Requisitos 7.1, 22.2**
  - [ ] 2.4 Criar `Biome.kt` (enum com 6 biomas e faixas de Floor) e `BiomePalette.kt` com paletas de cores
    - _Requisitos: 3.1, 3.3_

- [ ] 3. Implementar camada de persistência (Room Database)
  - [ ] 3.1 Criar `SaveStateEntity.kt`, `SaveStateDao.kt` e `AppDatabase.kt`
    - Configurar 3 snapshots de segurança com buffer circular
    - _Requisitos: 7.5, 7.6_
  - [ ] 3.2 Implementar `PersistenceManager.kt` com métodos `save()` e `restore()`
    - Lógica de 3 tentativas com intervalo de 100ms em caso de falha ao salvar
    - Restauração automática do snapshot anterior em caso de corrupção
    - _Requisitos: 7.2, 7.3, 7.4, 7.6, 7.7, 19.3, 19.4_
  - [ ]* 3.3 Escrever testes unitários para PersistenceManager
    - Testar serialização/desserialização (round-trip), recuperação de snapshot corrompido e comportamento com armazenamento cheio
    - _Requisitos: 22.2_

- [ ] 4. Implementar sistema de geração procedural (PCG)
  - [ ] 4.1 Criar `BSPMazeGenerator.kt` com algoritmo BSP recursivo e seed determinístico
    - _Requisitos: 2.1, 2.2, 2.3, 2.4, 2.5_
  - [ ] 4.2 Criar `MazeValidator.kt` com validação BFS de caminho válido
    - _Requisitos: 2.2, 2.6_
  - [ ]* 4.3 Escrever teste de propriedade: todo Maze gerado tem caminho válido
    - **Propriedade 1: Todo Maze gerado tem caminho válido**
    - **Valida: Requisitos 2.2, 2.6**
  - [ ]* 4.4 Escrever teste de propriedade: geração de Maze é determinística
    - **Propriedade 2: Geração de Maze é determinística**
    - **Valida: Requisito 2.7**
  - [ ] 4.5 Criar `BiomeParameters.kt` com parâmetros de densidade de paredes por faixa de Floor
    - _Requisitos: 2.3, 2.4, 2.5_
  - [ ]* 4.6 Escrever teste de propriedade: densidade de paredes respeita a faixa do Floor
    - **Propriedade 3: Densidade de paredes respeita a faixa do Floor**
    - **Valida: Requisitos 2.3, 2.4, 2.5**
  - [ ] 4.7 Criar `EntityPlacer.kt` com posicionamento de Monsters e Traps sem bloqueio do caminho válido
    - Implementar fórmulas de escalonamento: `min(2 + floor(n/10), 12)` Monsters e `min(1 + floor(n/15), 8)` Traps
    - _Requisitos: 2.6, 5.5, 5.6_
  - [ ]* 4.8 Escrever teste de propriedade: fórmulas de escalonamento respeitam os limites
    - **Propriedade 6: Fórmulas de escalonamento de entidades respeitam os limites**
    - **Valida: Requisitos 5.5, 5.6**
  - [ ] 4.9 Criar `PCGEngine.kt` orquestrando BSP + validação + EntityPlacer, com FloorSeed determinístico e Maze de fallback após 3 falhas
    - _Requisitos: 2.1, 2.7, 19.6_

- [ ] 5. Checkpoint — Verificar fundação
  - Garantir que todos os testes do PCG e PersistenceManager passam. Perguntar ao usuário se há dúvidas antes de prosseguir.

- [ ] 6. Implementar sistema de personagens e abstrações
  - [ ] 6.1 Criar `PlayableCharacter.kt` (classe abstrata) e `CompanionCharacter.kt` (classe abstrata)
    - Incluir `effectiveSpeed` com lógica de Slowdown (40% da velocidade base)
    - _Requisitos: 4.8, 24.1, 24.2, 24.3_
  - [ ] 6.2 Criar `Hero.kt` como implementação concreta de `PlayableCharacter`
    - _Requisitos: 24.1, 24.2_
  - [ ] 6.3 Criar `SkinSystem.kt` com `SkinDefinition` e resolução de skin por ID
    - _Requisitos: 24.4, 24.5_
  - [ ] 6.4 Criar `CharacterRegistry.kt` com registro centralizado de personagens
    - _Requisitos: 24.7_
  - [ ] 6.5 Criar `Spike.kt` como implementação concreta de `CompanionCharacter`
    - _Requisitos: 11.5, 24.3_
  - [ ] 6.6 Criar `SpikeAI.kt` com máquina de estados (8 estados: SEGUINDO, FAREJANDO, ALERTANDO, INCENTIVANDO, SLOWDOWN_PROPRIO, ENTUSIASMADO, CHAMANDO, CELEBRANDO)
    - _Requisitos: 11.1, 11.2, 11.3, 11.6, 11.7, 11.8, 11.9_
  - [ ]* 6.7 Escrever teste de propriedade: troca de personagem preserva progresso
    - **Propriedade 7: Troca de personagem preserva progresso**
    - **Valida: Requisito 24.8**

- [ ] 7. Implementar GameLoop e GameState
  - [ ] 7.1 Criar `GameState.kt` com estado global do jogo (Model)
    - _Requisitos: 21.6_
  - [ ] 7.2 Criar `GameLoop.kt` com thread dedicada, fixed timestep a 60fps e delta time
    - Implementar redução para 5fps em pausa e 30fps em temperatura crítica via `ThermalStatusCallback`
    - _Requisitos: 8.1, 8.6, 18.1, 18.2, 18.5, 21.6_

- [ ] 8. Implementar InputController
  - [ ] 8.1 Criar `FloatingJoystick.kt` com reposicionamento dinâmico e raio mínimo de 80dp
    - _Requisitos: 4.1, 4.7, 12.3, 13.4_
  - [ ] 8.2 Criar `DPadController.kt` como alternativa ao joystick flutuante
    - _Requisitos: 12.3_
  - [ ] 8.3 Criar `InputController.kt` orquestrando joystick e D-pad, com resposta háptica de 20ms em colisão com parede
    - Garantir latência máxima de 16ms do input ao movimento do Hero
    - _Requisitos: 4.2, 4.3, 4.4, 4.6, 4.7, 12.5_

- [ ] 9. Implementar Renderer e projeção isométrica
  - [ ] 9.1 Criar `IsometricProjection.kt` com conversão coordenadas mundo → tela
    - _Requisitos: 8.2, 13.5_
  - [ ] 9.2 Criar `SpriteCache.kt` com cache de Bitmaps pré-renderizados e `evictNonEssential()`
    - _Requisitos: 17.7, 20.1, 20.6_
  - [ ] 9.3 Criar `TileRenderer.kt` gerando tiles 32x32px por Bioma via Canvas (parede isométrica 3D, chão, decorativos)
    - Usar `Paint.filterBitmap = false` e `isAntiAlias = false` para estética pixel art
    - _Requisitos: 8.2, 17.1, 17.3_
  - [ ] 9.4 Criar `CharacterRenderer.kt` gerando sprites do Hero, Spike e Monsters via Canvas
    - Hero: 8 direções, 8+ frames de caminhada, 4 frames idle
    - Spike: 12+ frames por estado comportamental
    - Monsters: aparência procedural via `MonsterAppearance`
    - _Requisitos: 8.5, 17.1, 17.2, 2.8_
  - [ ] 9.5 Criar `ParticleSystem.kt` com partículas via `drawCircle`, `drawRect`, `drawPath` e interpolação de cor/opacidade
    - _Requisitos: 3.4, 17.4_
  - [ ] 9.6 Criar `HudRenderer.kt` gerando HUD via Canvas (score, slowdown, combo, temperatura, conquistas)
    - Suportar modo compacto (< 600dp) e expandido (≥ 600dp)
    - _Requisitos: 5.4, 5.7, 8.6, 13.2, 13.3, 17.5_
  - [ ] 9.7 Criar `Renderer.kt` orquestrando todos os sub-renderers com culling de tiles (máximo 200 draw calls/frame)
    - _Requisitos: 8.3, 8.7_
  - [ ]* 9.8 Escrever teste de propriedade: culling limita draw calls a no máximo 200
    - **Propriedade 8: Culling limita draw calls**
    - **Valida: Requisito 8.3**
  - [ ] 9.9 Criar `GameSurfaceView.kt` integrando SurfaceHolder com o Renderer
    - _Requisitos: 8.1, 13.6_

- [ ] 10. Checkpoint — Verificar renderização e input
  - Garantir que o jogo renderiza um mapa isométrico com Hero e Spike, aceita input do joystick e mantém 60fps. Perguntar ao usuário se há dúvidas antes de prosseguir.

- [ ] 11. Implementar AudioManager
  - [ ] 11.1 Criar `AudioManager.kt` com SoundPool para efeitos curtos e MediaPlayer para trilha ambiente por Bioma
    - Implementar fade de 1 segundo na transição entre Biomas
    - Implementar sons ambientes procedurais com intervalo aleatório de 5–30 segundos
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5_
  - [ ]* 11.2 Escrever testes unitários para AudioManager
    - Testar pausa em no máximo 100ms e transição de Bioma com fade
    - _Requisitos: 9.4, 22.4_

- [ ] 12. Implementar sistema de Score e ComboStreak
  - [ ] 12.1 Implementar cálculo de Score em `GameState.kt`: `baseScore = (10000 / tempoEmSegundos) * (1 + comboBonus)`
    - Implementar incremento de ComboStreak e bônus de 10% a cada múltiplo de 5
    - _Requisitos: 6.1, 6.2, 5.7, 5.8_
  - [ ]* 12.2 Escrever teste de propriedade: Score é monotonicamente decrescente com o tempo
    - **Propriedade 5: Score é monotonicamente decrescente com o tempo**
    - **Valida: Requisito 6.2**
  - [ ]* 12.3 Escrever testes unitários para cálculo de Score
    - Testar fórmula baseScore, acumulação de comboBonus e cálculo de tempo total do Floor
    - _Requisitos: 22.3_

- [ ] 13. Implementar telas de UI e ViewModel
  - [ ] 13.1 Criar `GameViewModel.kt` como ponte entre UI thread e GameLoop
    - _Requisitos: 21.1, 21.2, 21.4, 21.5_
  - [ ] 13.2 Criar `MainMenuActivity.kt` com tela principal (continuar, novo jogo, configurações, galeria de Biomas, recorde pessoal)
    - Implementar animação de introdução de até 4 segundos com skip por toque
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 10.1, 10.7_
  - [ ] 13.3 Criar `ScoreActivity.kt` com tela de Score (tempo, Maps, Slowdowns, ComboStreak, recorde pessoal, botões de ação)
    - _Requisitos: 6.1, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 10.5, 10.6_
  - [ ] 13.4 Criar `GameActivity.kt` como entry point Android, integrando GameViewModel, GameSurfaceView e ciclo de vida
    - Implementar `onPause`, `onResume`, `onDestroy`, `onSaveInstanceState` respeitando os limites de tempo (500ms para save, 100ms para pausar GameLoop/Audio)
    - Forçar modo landscape
    - _Requisitos: 1.3, 13.6, 18.1, 18.2, 21.1, 21.2, 21.3, 21.4, 21.5_
  - [ ] 13.5 Criar tela de configurações acessível do menu principal e do menu de pausa
    - Opções: volume música/efeitos, tamanho do joystick, posição do joystick, modo alto contraste, D-pad, feedback háptico, idioma
    - Suportar tamanhos de fonte do sistema Android
    - _Requisitos: 12.1, 12.2, 12.4, 12.5_

- [ ] 14. Implementar SocialManager e UpdateManager
  - [ ] 14.1 Criar `SocialManager.kt` gerando imagem 1080x1080 via Canvas com estatísticas e sprite do Spike
    - Implementar compartilhamento via `Intent.ACTION_SEND` com `FileProvider`
    - _Requisitos: 6.7, 6.8_
  - [ ] 14.2 Criar `UpdateManager.kt` com In-App Updates API (atualização flexível e imediata)
    - _Requisitos: 15.1, 15.2, 15.3, 15.4, 15.5_
  - [ ]* 14.3 Escrever testes unitários para UpdateManager
    - Testar comportamento com atualização opcional, obrigatória e falha de rede
    - _Requisitos: 22.4_

- [ ] 15. Implementar sistema de conquistas, notificações e progressão
  - [ ] 15.1 Implementar sistema de conquistas em `GameState.kt` com marcos: Floor 1, 10, 20, 40, 60, 80, 100, 120
    - Exibir notificação animada via `HudRenderer` sem interromper gameplay
    - _Requisitos: 10.2, 10.3_
  - [ ] 15.2 Implementar estatísticas cumulativas do Player em `PlayerStatistics` e persistência no SaveState
    - _Requisitos: 10.4_
  - [ ] 15.3 Implementar Leaderboard local (top 10 por Floor) em `PersistenceManager`
    - _Requisitos: 10.5, 14.4_
  - [ ] 15.4 Implementar notificação push de reengajamento (48h sem jogar) via WorkManager respeitando Doze Mode
    - _Requisitos: 10.8, 18.3_

- [ ] 16. Implementar gerenciamento de memória e ciclo de vida completo
  - [ ] 16.1 Garantir `Bitmap.recycle()` explícito ao encerrar cada Map no `Renderer`
    - _Requisitos: 20.1_
  - [ ] 16.2 Garantir `WeakReference` para Context, Activity e View em GameLoop, AudioManager e PersistenceManager
    - _Requisitos: 20.2_
  - [ ] 16.3 Implementar remoção de listeners/callbacks em `onDestroy` (SensorManager, AudioManager, ConnectivityManager)
    - _Requisitos: 20.3, 21.3_
  - [ ] 16.4 Implementar monitoramento de heap via `Debug.getNativeHeapAllocatedSize()` com log quando ultrapassar 80%
    - Chamar `SpriteCache.evictNonEssential()` em `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)`
    - _Requisitos: 8.4, 20.5, 20.6_

- [ ] 17. Implementar testes de integração do ciclo de vida e persistência
  - [ ]* 17.1 Escrever testes de integração para ciclo de vida Android
    - Testar salvamento correto em `onPause`, restauração em `onResume` e liberação de recursos em `onDestroy`
    - _Requisitos: 22.5_
  - [ ]* 17.2 Escrever testes de integração para fluxo de persistência
    - Testar salvamento automático ao final de cada Map, restauração após reinício do processo e integridade após múltiplos ciclos de save/restore
    - _Requisitos: 22.6_

- [ ] 18. Checkpoint final — Garantir que todos os testes passam
  - Garantir que todos os testes unitários, de propriedade e de integração passam. Perguntar ao usuário se há dúvidas antes de finalizar.

## Notas

- Tarefas marcadas com `*` são opcionais e podem ser puladas para um MVP mais rápido
- Cada tarefa referencia requisitos específicos para rastreabilidade
- Os checkpoints garantem validação incremental a cada fase
- Testes de propriedade validam invariantes universais com 100+ iterações via Kotest
- Testes unitários validam exemplos específicos e casos de borda
- Todos os nomes de métodos de teste, comentários e descrições devem ser escritos em português do Brasil (Requisito 22.7)
