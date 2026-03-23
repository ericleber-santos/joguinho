# Documento de Requisitos — Spike na Caverna

## Introdução

"Spike na Caverna" é um jogo mobile Android em Kotlin, classificação livre, com visão isométrica em modo landscape. O jogador controla um herói e seu cachorro Spike — um viralata brasileiro predominantemente branco com manchas pretas — explorando uma caverna abandonada que foi uma mina de ouro com até 120 andares, do tamanho de uma cidade subterrânea.

O jogo é contemplativo, bonito, animado e instigante. Cada andar é composto por múltiplos mapas gerados proceduralmente, com labirintos únicos, monstros variados e armadilhas. Não há morte — a penalidade é lentidão temporária. O objetivo é explorar o máximo de andares no menor tempo possível e tornar-se o herói da vila.

O jogo é projetado para sessões curtas e longas, com progressão persistente, compartilhamento social e loop de engajamento baseado em tempo, descoberta e superação pessoal.

---

## Glossário

- **Game**: O sistema de jogo "Spike na Caverna" como um todo.
- **Player**: O usuário humano que interage com o jogo.
- **Hero**: O personagem controlado pelo Player, representado em pixel art isométrica.
- **Spike**: O cachorro companheiro do Hero, viralata brasileiro branco com manchas pretas, que acompanha o Hero automaticamente.
- **Floor**: Um andar da caverna, numerado de 1 a 120, composto por um conjunto de Maps.
- **Map**: Um labirinto individual dentro de um Floor, gerado proceduralmente.
- **Maze**: A estrutura de labirinto de um Map, com paredes intransponíveis, caminhos, saída e elementos interativos.
- **Exit**: A saída de um Map que leva ao próximo Map ou ao próximo Floor.
- **Obstacle**: Elemento do Map que causa lentidão temporária ao Hero ou Spike quando ativado.
- **Monster**: Entidade animada do Map que se move e interage com o Hero, causando lentidão temporária ao contato.
- **Trap**: Elemento estático ou semi-estático do Map que causa lentidão temporária ao Hero quando ativado por proximidade ou movimento.
- **Slowdown**: Estado temporário do Hero ou Spike onde a velocidade de movimento é reduzida por um período definido.
- **Score**: Pontuação de um Floor, calculada com base no tempo total para completar todos os Maps do Floor.
- **Session**: Uma instância de jogo ativa, do momento em que o Player abre o app até fechar ou pausar.
- **SaveState**: Snapshot completo do estado do jogo persistido em armazenamento local.
- **PCG**: Procedural Content Generation — geração algorítmica de conteúdo de jogo.
- **Biome**: Tema visual e ambiental de um conjunto de Floors (ex: mina, riacho, plantação, dinossauros).
- **Leaderboard**: Ranking global e local de Players por andar alcançado e tempo total.
- **FloorSeed**: Valor numérico único que determina o layout procedural de um Floor para um Player específico.
- **ComboStreak**: Sequência de Maps completados sem receber Slowdown, que multiplica o bônus de tempo.
- **Lantern**: Recurso visual do Hero que ilumina a área ao redor em Biomes escuros.
- **Renderer**: Subsistema responsável por renderizar o jogo em tempo real usando Canvas ou OpenGL ES.
- **GameLoop**: Loop principal do jogo que processa input, atualiza estado e aciona o Renderer a cada frame.
- **InputController**: Subsistema que captura e processa entradas do Player (joystick virtual, botões).
- **AudioManager**: Subsistema responsável por música ambiente e efeitos sonoros.
- **PersistenceManager**: Subsistema responsável por salvar e restaurar o SaveState.
- **SocialManager**: Subsistema responsável por compartilhamento em redes sociais e WhatsApp.

---

## Requisitos

### Requisito 1: Inicialização e Tela de Entrada

**User Story:** Como Player, quero ver uma tela de entrada atraente ao abrir o jogo, para que eu me sinta imediatamente imerso no universo da Caverna do Spike.

#### Critérios de Aceitação

1. WHEN o Player abre o aplicativo pela primeira vez, THE Game SHALL exibir uma tela de introdução animada com o Hero e Spike entrando na caverna, com duração máxima de 4 segundos antes de permitir interação.
2. WHEN o Player abre o aplicativo com um SaveState existente, THE Game SHALL exibir a tela principal com a opção de continuar a partida salva em destaque.
3. THE Game SHALL exibir a tela principal em modo landscape com resolução adaptada à densidade de pixels do dispositivo Android.
4. WHEN o Player toca na tela durante a animação de introdução, THE Game SHALL pular a animação e exibir a tela principal imediatamente.
5. THE Game SHALL carregar todos os assets da tela principal em no máximo 3 segundos em dispositivos com Android 8.0 ou superior.

---

### Requisito 2: Geração Procedural de Mapas (PCG)

**User Story:** Como Player, quero que cada Map seja único e diferente dos anteriores, para que a exploração seja sempre surpreendente e instigante.

#### Critérios de Aceitação

1. WHEN um novo Map é iniciado, THE PCG SHALL gerar um Maze único usando o FloorSeed combinado com o índice do Map, garantindo que dois Players no mesmo Floor sempre recebam Mazes diferentes.
2. THE PCG SHALL garantir que todo Maze gerado tenha exatamente um caminho válido da posição inicial do Hero até o Exit.
3. WHEN o Floor pertence aos andares 1 a 20, THE PCG SHALL gerar Mazes com densidade de paredes entre 40% e 55% da área total do Map.
4. WHEN o Floor pertence aos andares 21 a 60, THE PCG SHALL gerar Mazes com densidade de paredes entre 55% e 70% da área total do Map.
5. WHEN o Floor pertence aos andares 61 a 120, THE PCG SHALL gerar Mazes com densidade de paredes entre 70% e 85% da área total do Map.
6. THE PCG SHALL posicionar Monsters, Traps e elementos decorativos de forma que nenhum bloqueie permanentemente o caminho válido até o Exit.
7. WHEN o Player seleciona "Reiniciar Andar" na tela de Score, THE PCG SHALL regenerar o Floor usando o mesmo FloorSeed, reproduzindo exatamente os mesmos Mazes, posições de Monsters e Traps.
8. THE PCG SHALL variar o visual dos Monsters usando combinações procedurais de paleta de cores, tamanho e animação, de forma que nenhum Monster repita exatamente a mesma aparência em dois Maps consecutivos.

---

### Requisito 3: Biomas e Progressão Visual

**User Story:** Como Player, quero que os ambientes da caverna mudem visualmente conforme avanço nos andares, para que a exploração seja visualmente recompensadora e contemplativa.

#### Critérios de Aceitação

1. THE Game SHALL organizar os 120 Floors em 6 Biomes distintos: Mina Abandonada (andares 1–20), Riachos Subterrâneos (21–40), Plantações e Abrigos (41–60), Construções Rochosas (61–80), Pomares e Aberturas (81–100) e Era dos Dinossauros (101–120).
2. WHEN o Hero entra em um novo Biome, THE Game SHALL exibir uma animação de transição de 2 segundos apresentando o nome e uma cena do novo ambiente.
3. THE Renderer SHALL aplicar paleta de cores, iluminação e elementos decorativos específicos de cada Biome em todos os Maps daquele Biome.
4. WHILE o Hero está nos Biomes de Riachos Subterrâneos ou Construções Rochosas, THE Renderer SHALL exibir efeitos de partículas de água ou poeira de pedra em pontos decorativos do Map.
5. WHILE o Hero está nos Biomes de andares 61 a 120, THE Renderer SHALL aplicar iluminação dinâmica com raio de visibilidade limitado ao alcance da Lantern do Hero, criando atmosfera de exploração.
6. THE Game SHALL exibir, em pontos específicos de cada Biome, aberturas visuais com paisagens de fundo animadas (céu estrelado, lava distante, floresta subterrânea) que não interferem na jogabilidade.
7. WHEN o Hero alcança o andar 40, 80 e 120, THE Game SHALL exibir uma cena cinemática curta (máximo 6 segundos) celebrando o marco de progressão.

---

### Requisito 4: Controle do Personagem

**User Story:** Como Player, quero controlar o Hero de forma responsiva e intuitiva na tela touch, para que o movimento seja fluido e preciso mesmo em situações de pressão.

#### Critérios de Aceitação

1. THE InputController SHALL exibir um joystick virtual na região esquerda da tela e um botão de corrida na região direita, ambos com área de toque mínima de 80x80dp.
2. WHEN o Player move o joystick virtual, THE Hero SHALL iniciar movimento na direção correspondente em no máximo 16ms (1 frame a 60fps).
3. THE Hero SHALL se mover nas 8 direções cardinais e diagonais com animação de caminhada correspondente à direção do movimento.
4. WHEN o Player pressiona o botão de corrida enquanto move o joystick, THE Hero SHALL aumentar a velocidade de movimento em 80% pelo tempo em que o botão estiver pressionado.
5. THE Spike SHALL seguir o Hero automaticamente com comportamento de pathfinding simples, mantendo distância máxima de 2 tiles do Hero.
6. WHEN o Hero colide com uma parede do Maze, THE InputController SHALL aplicar resposta háptica de 20ms e o Hero SHALL parar o movimento naquela direção sem atravessar a parede.
7. THE InputController SHALL suportar reposicionamento do joystick virtual por toque em qualquer ponto da metade esquerda da tela (floating joystick).
8. WHILE o Hero está em estado de Slowdown, THE Hero SHALL se mover a 40% da velocidade normal e a animação do Hero SHALL refletir visualmente o estado de lentidão.

---

### Requisito 5: Sistema de Obstáculos e Monstros

**User Story:** Como Player, quero enfrentar obstáculos e monstros desafiadores que me forcem a pensar no movimento e timing, para que a exploração seja instigante sem ser frustrante.

#### Critérios de Aceitação

1. WHEN o Hero entra em contato com um Monster, THE Game SHALL aplicar Slowdown ao Hero por 3 segundos e ao Spike por 2 segundos, e o Monster SHALL recuar 2 tiles da posição do Hero.
2. WHEN o Hero ativa uma Trap, THE Game SHALL aplicar Slowdown ao Hero por 2 segundos e exibir uma animação visual da Trap sendo ativada.
3. THE Game SHALL garantir que nenhum Obstacle cause morte, remoção do mapa ou perda de progresso ao Hero ou Spike.
4. WHEN o Hero está em estado de Slowdown, THE Game SHALL exibir um indicador visual de duração do Slowdown na HUD com contagem regressiva em segundos.
5. THE PCG SHALL escalar a quantidade de Monsters por Map de acordo com a fórmula: `min(2 + floor(floorNumber / 10), 12)` Monsters por Map.
6. THE PCG SHALL escalar a quantidade de Traps por Map de acordo com a fórmula: `min(1 + floor(floorNumber / 15), 8)` Traps por Map.
7. WHEN o Hero completa um Map sem receber nenhum Slowdown, THE Game SHALL incrementar o ComboStreak do Player e exibir um indicador visual de combo na HUD.
8. WHEN o ComboStreak do Player atinge múltiplos de 5, THE Game SHALL aplicar um bônus de 10% de redução no tempo registrado para o Score do Floor atual.
9. THE Monsters SHALL se mover em padrões procedurais variados (patrulha linear, circular, aleatória, perseguição por linha de visão) definidos pelo PCG no momento da geração do Map.

---

### Requisito 6: Sistema de Score e Tela de Parabéns

**User Story:** Como Player, quero ver meu desempenho ao completar um andar e ter opções claras de progressão, para que eu me sinta recompensado e motivado a continuar.

#### Critérios de Aceitação

1. WHEN o Hero alcança o Exit do último Map de um Floor, THE Game SHALL exibir a tela de Score com: número do Floor completado, tempo total do Floor em formato mm:ss:ms, número de Maps percorridos, número de Slowdowns recebidos e ComboStreak máximo.
2. THE Game SHALL calcular o Score final do Floor usando a fórmula: `baseScore = (10000 / tempoEmSegundos) * (1 + comboBonus)`, onde comboBonus é a soma dos bônus de ComboStreak acumulados.
3. THE Game SHALL exibir na tela de Score três opções: "Próximo Andar", "Salvar e Sair" e "Reiniciar Andar".
4. WHEN o Player seleciona "Próximo Andar", THE Game SHALL gerar o próximo Floor com novo FloorSeed e iniciar o primeiro Map do novo Floor.
5. WHEN o Player seleciona "Reiniciar Andar", THE Game SHALL regenerar o Floor com o mesmo FloorSeed e reiniciar o timer do Floor.
6. WHEN o Player seleciona "Salvar e Sair", THE Game SHALL persistir o SaveState completo e retornar à tela principal.
7. THE Game SHALL exibir na tela de Score um botão de compartilhamento que gera uma imagem com: nome do jogo "Spike na Caverna", andar alcançado, tempo total acumulado em horas e quantidade total de Maps percorridos.
8. WHEN o Player toca no botão de compartilhamento, THE SocialManager SHALL abrir o seletor nativo do Android com a imagem gerada, compatível com WhatsApp, Instagram e demais apps de compartilhamento.
9. THE Game SHALL exibir na tela de Score o melhor tempo pessoal do Player para aquele Floor, caso exista, com indicação visual de novo recorde quando o tempo atual for menor.

---

### Requisito 7: Persistência e Continuidade

**User Story:** Como Player, quero que meu progresso seja salvo automaticamente em qualquer situação, para que eu nunca perca o que conquistei por interrupções externas.

#### Critérios de Aceitação

1. THE PersistenceManager SHALL salvar o SaveState automaticamente ao final de cada Map completado, contendo: Floor atual, Map atual, FloorSeed, posição do Hero, estado de todos os Monsters e Traps, timer do Floor e Score acumulado.
2. WHEN o sistema Android envia o evento onPause à Activity do jogo, THE PersistenceManager SHALL salvar o SaveState em no máximo 500ms.
3. WHEN o sistema Android envia o evento onLowMemory, THE PersistenceManager SHALL salvar o SaveState imediatamente antes de liberar recursos.
4. WHEN o Player reabre o aplicativo após interrupção, THE Game SHALL restaurar o SaveState e posicionar o Hero exatamente no início do Map onde estava quando o jogo foi interrompido.
5. THE PersistenceManager SHALL armazenar o SaveState em armazenamento interno do dispositivo usando Room Database, com backup automático via Android Auto Backup para conta Google do usuário.
6. THE PersistenceManager SHALL manter os últimos 3 SaveStates como snapshots de segurança, permitindo recuperação em caso de corrupção do SaveState mais recente.
7. WHEN o SaveState mais recente está corrompido, THE PersistenceManager SHALL restaurar o SaveState imediatamente anterior e notificar o Player com mensagem informando o Map de retorno.

---

### Requisito 8: Performance e Renderização

**User Story:** Como Player, quero que o jogo rode de forma fluida e bonita no meu dispositivo Android, para que a experiência seja imersiva sem travamentos ou quedas de frame.

#### Critérios de Aceitação

1. THE GameLoop SHALL manter taxa de atualização de 60 frames por segundo em dispositivos com Android 8.0 ou superior e processador octa-core ou superior.
2. THE Renderer SHALL renderizar o Map em visão isométrica com tiles de pixel art de 32x32 pixels base, escalados proporcionalmente à resolução do dispositivo.
3. THE Renderer SHALL utilizar técnica de culling para renderizar apenas os tiles visíveis na viewport atual, limitando o número de draw calls a no máximo 200 por frame.
4. THE Game SHALL consumir no máximo 150MB de memória RAM durante gameplay ativa em dispositivos com 2GB de RAM.
5. THE Renderer SHALL aplicar animações de sprite com interpolação suave para o Hero, Spike e Monsters, usando spritesheets com no mínimo 8 frames por ciclo de animação.
6. WHEN o dispositivo reporta temperatura crítica via ThermalStatusCallback, THE GameLoop SHALL reduzir a taxa de atualização para 30fps e notificar o Player com ícone de temperatura na HUD.
7. THE Renderer SHALL suportar modo de acessibilidade com contraste aumentado, ativável nas configurações do jogo, que aumenta a distinção visual entre Hero, Monsters e paredes do Maze.

---

### Requisito 9: Áudio e Atmosfera

**User Story:** Como Player, quero que o jogo tenha trilha sonora e efeitos sonoros que reforcem a atmosfera contemplativa e de exploração, para que a imersão seja completa.

#### Critérios de Aceitação

1. THE AudioManager SHALL reproduzir trilha sonora ambiente específica para cada Biome, com loop contínuo e transição suave (fade de 1 segundo) ao mudar de Biome.
2. THE AudioManager SHALL reproduzir efeitos sonoros distintos para: passos do Hero em diferentes superfícies (pedra, água, terra), ativação de Trap, contato com Monster, conclusão de Map e conclusão de Floor.
3. THE AudioManager SHALL reproduzir sons ambientes procedurais (gotejamento de água, vento subterrâneo, sons de animais distantes) em intervalos aleatórios entre 5 e 30 segundos, específicos por Biome.
4. WHEN o Player pausa o jogo ou o app vai para background, THE AudioManager SHALL pausar toda reprodução de áudio em no máximo 100ms.
5. THE Game SHALL respeitar o volume do sistema Android para música e efeitos sonoros, com controles independentes de volume nas configurações do jogo.
6. WHERE o dispositivo suporta áudio espacial (Android 12+), THE AudioManager SHALL aplicar posicionamento 3D nos sons de Monsters e Traps, com volume proporcional à distância do Hero.

---

### Requisito 10: Progressão, Engajamento e Retenção

**User Story:** Como Player, quero sentir que estou progredindo e sendo recompensado ao longo do tempo, para que eu queira voltar ao jogo todos os dias.

#### Critérios de Aceitação

1. THE Game SHALL manter um registro permanente do maior Floor alcançado pelo Player, exibido na tela principal como "Recorde Pessoal".
2. THE Game SHALL exibir um sistema de conquistas desbloqueáveis baseado em marcos: primeiro Floor completado, Floor 10, Floor 20, Floor 40, Floor 60, Floor 80, Floor 100 e Floor 120.
3. WHEN o Player desbloqueia uma conquista, THE Game SHALL exibir uma notificação animada na tela com o nome e ícone da conquista, sem interromper o gameplay.
4. THE Game SHALL registrar estatísticas cumulativas do Player: total de Maps percorridos, total de horas jogadas, total de Slowdowns recebidos e total de ComboStreaks máximos.
5. THE Game SHALL exibir um Leaderboard local com os 10 melhores tempos por Floor do próprio dispositivo, acessível na tela de Score.
6. WHEN o Player completa um Floor com tempo melhor que seu recorde pessoal para aquele Floor, THE Game SHALL exibir animação de "Novo Recorde" com efeitos visuais de celebração por 2 segundos.
7. THE Game SHALL oferecer, na tela principal, acesso a uma galeria de Biomes já visitados pelo Player, exibindo uma cena estática animada de cada Biome desbloqueado como recompensa visual de coleção.
8. WHEN o Player não abre o jogo por 48 horas, THE Game SHALL enviar uma notificação push com mensagem temática convidando o Player a continuar a exploração, respeitando as permissões de notificação do Android.

---

### Requisito 11: Spike — Comportamento e Personalidade

**User Story:** Como Player, quero que o Spike seja um companheiro com personalidade expressiva e papel de torcedor ativo, para que eu me apegue ao personagem, sinta que não estou explorando sozinho e me sinta encorajado a continuar mesmo diante de obstáculos.

#### Critérios de Aceitação

1. THE Spike SHALL exibir animações de comportamento contextual: farejar o chão ao parar por mais de 3 segundos, abanar o rabo ao completar um Map, encolher ao receber Slowdown e olhar na direção do Exit quando o Hero está próximo.
2. WHEN o Hero recebe Slowdown, THE Spike SHALL exibir animação de preocupação (orelhas abaixadas, olhos grandes) por 1 segundo antes de também entrar em Slowdown, seguida de animação de incentivo (latido animado, rabo abanando vigorosamente) indicando encorajamento ao Hero para continuar.
3. THE Spike SHALL emitir sons de latido curto ao detectar um Monster a menos de 5 tiles de distância, servindo como alerta sonoro ao Player.
4. WHEN o Hero completa um Floor, THE Spike SHALL executar animação de comemoração (pular, girar, abanar) por 3 segundos na tela de Score.
5. THE Spike SHALL ter aparência visual consistente em todos os Biomes: viralata brasileiro predominantemente branco com manchas pretas no dorso e patas, em pixel art isométrica com no mínimo 12 frames de animação por estado.
6. WHEN o Hero permanece parado por mais de 5 segundos sem input do Player, THE Spike SHALL emitir latidos animados em direção ao Hero e exibir animação de chamado (corrida curta em direção ao Exit e retorno), incentivando o Player a continuar o movimento.
7. WHEN o Hero está a menos de 3 tiles do Exit, THE Spike SHALL exibir animação de entusiasmo (orelhas erguidas, rabo em alta velocidade, postura de alerta animado) e emitir latido de excitação, reforçando visualmente a proximidade da saída.
8. WHEN o Hero supera um Obstacle ou Trap sem receber Slowdown, THE Spike SHALL exibir animação de celebração imediata (salto curto, abanar vigoroso) por 1 segundo, comemorando a superação do obstáculo junto ao Hero.
9. WHEN o Hero recebe Slowdown, THE Spike SHALL exibir uma mensagem visual de incentivo — balão de fala com ícone animado de encorajamento (ex: pata levantada, coração) — por 2 segundos, em substituição a uma reação exclusivamente negativa, reforçando o papel motivador do Spike.

---

### Requisito 12: Configurações e Acessibilidade

**User Story:** Como Player, quero personalizar a experiência de jogo e ter opções de acessibilidade, para que o jogo seja confortável para diferentes perfis de jogadores.

#### Critérios de Aceitação

1. THE Game SHALL oferecer tela de configurações acessível a partir da tela principal e do menu de pausa, com opções de: volume de música, volume de efeitos, tamanho do joystick virtual, posição do joystick (esquerda/direita) e modo de alto contraste.
2. THE Game SHALL suportar o tamanho de fonte do sistema Android (pequeno, médio, grande, extra grande) em todos os textos da interface, sem quebrar o layout.
3. THE InputController SHALL oferecer opção de controle alternativo por botões direcionais fixos (D-pad) como alternativa ao joystick virtual flutuante.
4. THE Game SHALL exibir todos os textos da interface em português do Brasil como idioma padrão, com suporte a inglês como idioma alternativo nas configurações.
5. WHERE o dispositivo suporta vibração, THE Game SHALL oferecer opção de feedback háptico nas configurações, habilitado por padrão, com intensidade ajustável em três níveis.

---

### Requisito 13: Responsividade para Celulares e Tablets Android

**User Story:** Como Player, quero que o jogo funcione bem em qualquer dispositivo Android — celular ou tablet — para que a experiência seja adequada independentemente do tamanho da tela.

#### Critérios de Aceitação

1. THE Renderer SHALL adaptar o tamanho base dos tiles isométricos proporcionalmente à densidade de pixels e ao tamanho físico da tela, garantindo que o Map ocupe no mínimo 80% da área útil da viewport em qualquer dispositivo.
2. WHEN o dispositivo tem largura de tela menor que 600dp, THE Game SHALL exibir o HUD em modo compacto, reduzindo o tamanho dos indicadores e posicionando-os nas bordas da tela sem sobrepor a área de jogo.
3. WHEN o dispositivo tem largura de tela igual ou maior que 600dp (tablet), THE Game SHALL exibir o HUD em modo expandido, com indicadores maiores e espaçamento aumentado entre os elementos de controle.
4. THE InputController SHALL escalar a área de toque do joystick virtual e dos botões proporcionalmente à densidade de pixels do dispositivo, mantendo área mínima de 80x80dp em qualquer resolução.
5. THE Renderer SHALL manter a proporção isométrica correta dos tiles e personagens em todas as resoluções suportadas, sem distorção visual em telas com aspect ratio entre 16:9 e 21:9.
6. WHEN o dispositivo muda de orientação durante o jogo, THE Game SHALL manter o modo landscape forçado e ignorar a rotação do sistema operacional.

---

### Requisito 14: Funcionamento Offline Total

**User Story:** Como Player, quero jogar sem precisar de conexão com internet, para que eu possa explorar a caverna em qualquer lugar, mesmo sem sinal.

#### Critérios de Aceitação

1. THE Game SHALL executar todas as funcionalidades principais — gameplay, geração procedural de Maps, persistência de SaveState, cálculo de Score, sistema de conquistas e Leaderboard local — sem nenhuma conexão com internet.
2. THE PersistenceManager SHALL armazenar todos os dados de progresso, estatísticas e conquistas exclusivamente em armazenamento local do dispositivo, sem depender de serviços externos para leitura ou escrita.
3. WHEN o dispositivo não tem conexão com internet, THE Game SHALL iniciar e funcionar normalmente sem exibir erros, avisos de conectividade ou telas de carregamento relacionadas à rede.
4. THE Game SHALL disponibilizar o Leaderboard local com os dados armazenados no dispositivo independentemente do estado da conexão de rede.
5. WHEN o dispositivo recupera conexão com internet durante uma Session, THE Game SHALL sincronizar dados de conquistas e estatísticas em segundo plano sem interromper o gameplay.

---

### Requisito 15: Sistema de Atualização do Aplicativo

**User Story:** Como Player, quero ser informado sobre atualizações disponíveis de forma não intrusiva, para que eu possa decidir quando atualizar sem ser forçado a interromper minha sessão.

#### Critérios de Aceitação

1. WHEN o dispositivo tem conexão com internet e o Game detecta uma atualização opcional disponível, THE Game SHALL exibir um diálogo não bloqueante na tela principal perguntando ao Player se deseja atualizar, com opções "Atualizar agora" e "Lembrar depois".
2. WHEN o Player seleciona "Lembrar depois" no diálogo de atualização opcional, THE Game SHALL dispensar o diálogo e não exibi-lo novamente na mesma Session.
3. WHEN o dispositivo tem conexão com internet e o Game detecta uma atualização obrigatória disponível, THE Game SHALL exibir um diálogo bloqueante informando que o jogo precisa ser atualizado para continuar, com apenas a opção "Atualizar agora".
4. WHEN o dispositivo não tem conexão com internet e existe uma atualização obrigatória pendente, THE Game SHALL informar ao Player que é necessário conectar-se à internet para atualizar o jogo antes de continuar.
5. IF o Game não consegue verificar a disponibilidade de atualização por falha de rede, THEN THE Game SHALL prosseguir normalmente sem exibir mensagem de erro ao Player.

---

### Requisito 16: Modelo de Negócio e Preparação para Monetização Futura

**User Story:** Como Player, quero jogar sem anúncios, compras ou paywalls, para que a experiência seja completamente gratuita e sem interrupções comerciais.

#### Critérios de Aceitação

1. THE Game SHALL disponibilizar todas as funcionalidades de gameplay, progressão, conquistas e compartilhamento sem custo, sem exibir anúncios, sem oferecer compras dentro do aplicativo e sem restringir conteúdo por paywall.
2. THE Game SHALL estruturar o código de monetização em módulos isolados e desativados, de forma que funcionalidades futuras de anúncios ou compras possam ser habilitadas sem refatoração da lógica principal de jogo.
3. THE Game SHALL reservar, no layout do HUD e das telas de Score e tela principal, espaços opcionais identificados em código que possam receber banners ou botões de monetização futura sem redesenho das telas existentes.

---

### Requisito 17: Assets Visuais Gerados Programaticamente

**User Story:** Como desenvolvedor, quero que todos os sprites, animações e elementos visuais do jogo sejam gerados em código Kotlin usando Canvas, para que o jogo não dependa de arquivos de imagem externos e a arte seja totalmente controlada por código.

#### Critérios de Aceitação

1. THE Renderer SHALL gerar todos os sprites do Hero, Spike, Monsters, Traps e tiles de Biome em tempo de inicialização usando Android Canvas com operações de desenho vetorial e pixel art procedural, sem carregar arquivos de imagem externos (PNG, JPEG, WebP ou similares).
2. THE Renderer SHALL gerar animações de sprite para o Hero, Spike e Monsters como sequências de frames desenhados programaticamente em Kotlin, com no mínimo 8 frames por ciclo de animação para estados de movimento e no mínimo 4 frames para estados de idle.
3. THE Renderer SHALL gerar os tiles de cada Biome com paleta de cores, texturas e detalhes decorativos definidos por parâmetros de código, permitindo variação visual entre Biomes sem arquivos de asset externos.
4. THE Renderer SHALL gerar efeitos de partículas (poeira, água, faíscas, brilhos) usando Canvas drawCircle, drawRect e drawPath com interpolação de cor e opacidade por frame, sem uso de bibliotecas de partículas externas.
5. THE Renderer SHALL gerar os elementos de HUD — barras de status, ícones de conquista, indicadores de Slowdown e ComboStreak — como formas geométricas e texto desenhados via Canvas, sem dependência de arquivos de imagem para ícones.
6. THE Renderer SHALL gerar animações de UI (transições de tela, notificações de conquista, efeito de "Novo Recorde") usando interpolação de propriedades Canvas (escala, opacidade, translação) implementada no GameLoop, sem uso de arquivos de animação externos.
7. WHEN o Game é inicializado, THE Renderer SHALL pré-renderizar e armazenar em cache os sprites e tiles mais utilizados como Bitmaps em memória, de forma que o custo de geração procedural ocorra apenas uma vez por Session.

---

### Requisito 18: Otimização de Consumo de Bateria

**User Story:** Como Player, quero que o jogo consuma bateria de forma eficiente, para que eu possa jogar por longos períodos sem drenar o dispositivo rapidamente.

#### Critérios de Aceitação

1. WHEN o sistema Android envia o evento onPause à Activity do jogo, THE GameLoop SHALL suspender completamente o processamento de frames em no máximo 100ms, cessando toda atualização de estado e renderização.
2. WHEN o sistema Android envia o evento onResume à Activity do jogo, THE GameLoop SHALL retomar o processamento de frames a partir do estado salvo em no máximo 200ms.
3. THE Game SHALL utilizar o mecanismo Doze Mode do Android corretamente, adiando operações não críticas de rede e persistência para janelas de manutenção definidas pelo sistema operacional.
4. THE Game SHALL limitar o uso de WakeLock ao mínimo necessário, adquirindo WakeLock apenas durante operações de persistência crítica e liberando-o imediatamente após a conclusão.
5. WHILE o jogo está em estado de pausa, THE GameLoop SHALL reduzir a taxa de atualização para no máximo 5fps, mantendo apenas a animação mínima da tela de pausa ativa.
6. THE Game SHALL evitar operações de processamento em background não relacionadas ao estado atual do jogo, incluindo geração procedural antecipada de Maps não solicitados pelo Player.

---

### Requisito 19: Estabilidade — Zero Crashes

**User Story:** Como Player, quero que o jogo nunca trave ou feche inesperadamente, para que minha sessão de jogo nunca seja interrompida por falhas técnicas.

#### Critérios de Aceitação

1. THE Game SHALL envolver todos os pontos críticos de execução — GameLoop, Renderer, PersistenceManager, PCG e AudioManager — em blocos de tratamento de exceção que impeçam qualquer exceção não tratada de se propagar ao sistema operacional Android.
2. IF o Renderer encontrar um erro durante a renderização de um frame, THEN THE Renderer SHALL registrar o erro em log interno, pular o frame com falha e continuar a renderização do próximo frame sem interromper o GameLoop.
3. IF o PersistenceManager encontrar um erro ao salvar o SaveState, THEN THE PersistenceManager SHALL realizar no máximo 3 tentativas de escrita com intervalo de 100ms entre cada tentativa antes de registrar falha em log interno.
4. IF o PersistenceManager encontrar um erro ao carregar o SaveState, THEN THE PersistenceManager SHALL tentar restaurar o SaveState de segurança imediatamente anterior e notificar o Player com mensagem descritiva do Map de retorno.
5. THE Game SHALL registrar todas as exceções capturadas em arquivo de log interno no armazenamento do dispositivo, com timestamp, classe de origem e stack trace, sem expor informações ao Player.
6. IF o PCG falhar na geração de um Maze válido após 3 tentativas, THEN THE PCG SHALL utilizar um Maze de fallback pré-definido para o intervalo de Floor correspondente, garantindo que o jogo continue sem interrupção.

---

### Requisito 20: Prevenção de Vazamento de Memória

**User Story:** Como desenvolvedor, quero que o jogo gerencie memória corretamente durante todo o ciclo de vida, para que o consumo de RAM não cresça indefinidamente ao longo de uma sessão.

#### Critérios de Aceitação

1. WHEN o Game encerra um Map, THE Renderer SHALL liberar explicitamente todos os objetos Bitmap gerados para aquele Map que não estejam no cache de sprites reutilizáveis, chamando `Bitmap.recycle()` antes de remover a referência.
2. THE Game SHALL utilizar WeakReference para referências a Context, Activity e View em todos os componentes que sobrevivem ao ciclo de vida da Activity, incluindo GameLoop, AudioManager e PersistenceManager.
3. WHEN a Activity do jogo executa onDestroy, THE Game SHALL remover todos os listeners, callbacks e observers registrados em componentes do sistema Android, incluindo SensorManager, AudioManager e ConnectivityManager.
4. THE Game SHALL liberar todos os recursos Canvas e Paint alocados durante a renderização ao final de cada ciclo do GameLoop, evitando acúmulo de objetos não coletados pelo garbage collector.
5. WHILE o jogo está em execução, THE Game SHALL monitorar o uso de heap via `Debug.getNativeHeapAllocatedSize()` e registrar em log interno quando o uso ultrapassar 80% do limite de memória do processo.
6. WHEN o sistema Android envia o evento onTrimMemory com nível TRIM_MEMORY_RUNNING_LOW ou superior, THE Game SHALL liberar o cache de sprites não essenciais e regenerá-los sob demanda na próxima utilização.

---

### Requisito 21: Ciclo de Vida Android

**User Story:** Como desenvolvedor, quero que o jogo respeite rigorosamente o ciclo de vida Android, para que o comportamento seja correto e previsível em todas as transições de estado do sistema operacional.

#### Critérios de Aceitação

1. WHEN o sistema Android invoca onPause na Activity do jogo, THE Game SHALL pausar o GameLoop, pausar o AudioManager e iniciar o salvamento do SaveState, completando todas essas operações em no máximo 500ms.
2. WHEN o sistema Android invoca onResume na Activity do jogo, THE Game SHALL retomar o GameLoop e o AudioManager a partir do estado preservado, sem reiniciar o Map ou perder o progresso do timer.
3. WHEN o sistema Android invoca onDestroy na Activity do jogo, THE Game SHALL liberar todos os recursos alocados — Bitmaps, threads do GameLoop, conexões do PersistenceManager e instâncias do AudioManager — antes que o método retorne.
4. WHEN o sistema Android invoca onSaveInstanceState na Activity do jogo, THE Game SHALL persistir o estado de UI — posição de scroll de menus, tela ativa e configurações de sessão — no Bundle fornecido pelo sistema.
5. WHEN o sistema Android recria a Activity por mudança de configuração (rotação, mudança de idioma do sistema, alteração de tamanho de fonte), THE Game SHALL restaurar o estado de UI a partir do Bundle salvo e manter o modo landscape forçado sem reiniciar o Map em progresso.
6. THE Game SHALL executar o GameLoop em uma thread dedicada separada da UI thread, garantindo que operações de renderização e atualização de estado não bloqueiem o thread principal do Android.

---

### Requisito 22: Testes Automatizados

**User Story:** Como desenvolvedor, quero ter testes automatizados cobrindo as partes críticas do sistema, para que regressões sejam detectadas rapidamente e a confiança no código seja mantida ao longo do desenvolvimento.

#### Critérios de Aceitação

1. THE Game SHALL incluir testes unitários para o módulo PCG verificando: geração de Maze com caminho válido garantido, reprodutibilidade com mesmo FloorSeed, densidade de paredes dentro dos intervalos por faixa de Floor e posicionamento de Monsters e Traps sem bloqueio do caminho válido.
2. THE Game SHALL incluir testes unitários para o PersistenceManager verificando: serialização e desserialização do SaveState (propriedade de round-trip), recuperação a partir de SaveState de segurança em caso de corrupção e comportamento correto com armazenamento cheio.
3. THE Game SHALL incluir testes unitários para o cálculo de Score verificando: aplicação correta da fórmula baseScore, acumulação de bônus de ComboStreak e cálculo de tempo total do Floor.
4. THE Game SHALL incluir testes unitários para o sistema de atualização verificando: comportamento com atualização opcional disponível, comportamento com atualização obrigatória disponível e comportamento com falha de rede na verificação.
5. THE Game SHALL incluir testes de integração para o ciclo de vida Android verificando: salvamento correto do SaveState em onPause, restauração correta do estado em onResume e liberação de recursos em onDestroy.
6. THE Game SHALL incluir testes de integração para o fluxo de persistência verificando: salvamento automático ao final de cada Map, restauração do estado após reinício do processo e integridade dos dados após múltiplos ciclos de salvar e restaurar.
7. THE Game SHALL escrever todos os nomes de métodos de teste, comentários e descrições de teste em português do Brasil.

---

### Requisito 23: Documentação Técnica

**User Story:** Como desenvolvedor, quero uma documentação técnica completa ao final do projeto, para que qualquer programador — incluindo iniciantes — possa entender as decisões de arquitetura, os padrões utilizados e o raciocínio por trás de cada escolha técnica.

#### Critérios de Aceitação

1. WHEN o projeto é concluído, THE Game SHALL incluir um documento de documentação técnica em português do Brasil cobrindo: arquitetura geral do sistema, descrição de cada subsistema (GameLoop, Renderer, PCG, PersistenceManager, AudioManager, InputController, SocialManager), padrões de design utilizados e justificativa para cada escolha.
2. THE documentação técnica SHALL explicar o "porquê" de cada decisão técnica relevante — não apenas o "o quê" — incluindo: por que o GameLoop roda em thread separada, por que WeakReference é usada em determinados contextos, por que Room Database foi escolhido para persistência e por que Canvas foi preferido a OpenGL ES para renderização.
3. THE documentação técnica SHALL explicar os aspectos de performance relevantes: por que culling reduz draw calls, como o cache de Bitmaps reduz o custo de geração procedural, como o Doze Mode afeta operações em background e por que a taxa de frames é reduzida em pausa.
4. THE documentação técnica SHALL ser escrita em linguagem acessível a programadores juniores, evitando jargão sem explicação, utilizando analogias quando necessário e incluindo exemplos de código comentados para os padrões mais complexos.
5. THE Game SHALL incluir comentários em português do Brasil em todos os arquivos de código-fonte, explicando a responsabilidade de cada classe, o propósito de cada método não trivial e o raciocínio por trás de implementações que não sejam imediatamente óbvias.
6. THE documentação técnica SHALL incluir um diagrama textual (em formato ASCII ou Mermaid) do fluxo principal do jogo: inicialização → tela principal → geração de Floor → gameplay → tela de Score → persistência, com indicação dos subsistemas envolvidos em cada etapa.

---

### Requisito 24: Arquitetura Extensível de Personagens e Skins

**User Story:** Como desenvolvedor, quero que o sistema de personagens seja arquitetado com abstrações extensíveis desde o início, para que personagens adicionais, Spike como personagem controlável e skins para o Hero e o Spike possam ser incorporados no futuro sem refatoração da lógica principal do jogo.

#### Critérios de Aceitação

1. THE Game SHALL definir uma abstração `PlayableCharacter` que encapsule os atributos e comportamentos comuns a qualquer personagem controlável — velocidade base, estados de animação, resposta a Slowdown e interação com o InputController — de forma que o Hero seja a implementação concreta inicial dessa abstração.
2. THE Game SHALL implementar o Hero como instância de `PlayableCharacter`, garantindo que a lógica do GameLoop, InputController e Renderer referenciem exclusivamente a abstração `PlayableCharacter` e não a implementação concreta do Hero.
3. THE Game SHALL definir uma abstração `CompanionCharacter` que encapsule os atributos e comportamentos de personagens companheiros não controláveis — pathfinding, estados de animação contextual e reações a eventos do jogo — de forma que o Spike seja a implementação concreta inicial dessa abstração.
4. THE Game SHALL implementar um `SkinSystem` desacoplado do Renderer, onde cada `PlayableCharacter` e `CompanionCharacter` referencie um identificador de skin que o Renderer resolve em tempo de renderização, sem que a lógica de jogo conheça os detalhes visuais da skin aplicada.
5. THE SkinSystem SHALL suportar a definição de skins como conjuntos de parâmetros de geração procedural (paleta de cores, variações de forma, detalhes decorativos) aplicados sobre o sprite base do personagem, sem alterar a estrutura de animação ou os estados de comportamento do personagem.
6. THE Game SHALL estruturar o SaveState de forma que o campo de personagem ativo e o campo de skin selecionada sejam opcionais com valores padrão definidos, garantindo que SaveStates existentes sem esses campos sejam carregados corretamente sem migração obrigatória.
7. WHEN um novo `PlayableCharacter` é adicionado ao jogo, THE Game SHALL registrá-lo em um `CharacterRegistry` centralizado sem modificar o GameLoop, o InputController ou o Renderer, de forma que a adição de personagens seja isolada ao registro e à implementação concreta.
8. THE Game SHALL garantir que a substituição do personagem ativo entre sessões — selecionando Hero ou qualquer futuro personagem — não altere o FloorSeed, o progresso de Floor, o Score acumulado ou qualquer outro dado de progressão armazenado no SaveState.
