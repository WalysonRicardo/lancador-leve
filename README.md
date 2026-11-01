# Lançador Leve — launcher para o projetor HY320

Uma home leve para o projetor HY320 (Android 11), com Netflix, YouTube, Pluto TV, Globoplay, SBT+ e Stremio, e um controle pelo celular com **comandos de voz** e **reordenação dos apps arrastando com o dedo**.

## Baixar o APK

**Última versão:** https://github.com/WalysonRicardo/lancador-leve/releases/latest/download/lancador-leve.apk

Cada envio para a branch `main` gera um APK novo em **Releases**, compilado pelo GitHub Actions. Todas as versões saem com a mesma assinatura (chave guardada nos Secrets), então dá para instalar por cima da anterior sem perder a ordem da home.

Instalar pelo computador: `adb -s IP_DO_PROJETOR:5555 install -r lancador-leve.apk`
(ou rode `otimizacao/otimizar`, que baixa e instala sozinho).

---

## O que tem no repositório

| Pasta | O que é |
|---|---|
| `app/` | Código do launcher (Kotlin, sem AndroidX/Compose) com o servidor do controle embutido |
| `app/src/main/assets/remote.html` | A página do controle que abre no celular |
| `otimizacao/` | Scripts ADB que deixam o Android do projetor mais leve, com backup e volta atrás |
| `.github/workflows/build.yml` | Compila e publica o APK automaticamente |

## Por que não uma ROM nova

O HY320 usa o chip Allwinner H713. Foco, trapézio, motor e painel dependem de drivers fechados do fabricante, e não há imagem de fábrica pública e confiável para esse modelo. Gravar um Android montado do zero pode deixar o projetor sem imagem, sem como voltar.

O "Android adaptado" é o sistema do aparelho, enxugado por ADB: apps inúteis desativados (não apagados), animações curtas, congelamento de apps parados, apps principais pré-compilados e este launcher no lugar do original. Tudo volta ao normal com `restaurar`.

---

## Passo 1 — Preparar o computador

1. Baixe o **Android Platform Tools** (site oficial do Android Developers) e coloque a pasta `otimizacao` dentro dele (ou ponha o `adb` no PATH).
2. No projetor: **Configurações › Opções do desenvolvedor › Depuração USB/ADB = ligada** (e **Depuração por rede**, se existir).
3. Descubra o IP do projetor (Configurações › Rede) e teste: `adb connect 192.168.0.42:5555`. Aceite no projetor marcando **Sempre permitir**.

> "Connection refused"? Ligue o projetor por USB ao PC e rode `adb tcpip 5555`. Nesse caso a porta fecha ao reiniciar, e o controle pelo celular para até repetir o comando. Muitos projetores H713 já vêm com a 5555 aberta de fábrica.

## Passo 2 — Otimizar e instalar

1. `otimizacao/listar-pacotes` (`.bat` no Windows, `.sh` no Linux/Mac) gera `pacotes-instalados.txt`. Passe para `remover.txt` os apps do fabricante que você não usa.
2. `otimizacao/otimizar`: faz o backup, aplica os ajustes, desativa a lista, **baixa e instala o Lançador Leve**, instala outros APKs que estiverem em `apks/`, define a home e pré-compila os apps principais.
3. Reinicie: `adb -s IP:5555 reboot`.

**Desfazer tudo:** `otimizacao/restaurar`. O launcher original não é desativado (ele guarda os ajustes de foco, trapézio e HDMI).

## Passo 3 — Controle pelo celular

1. Celular e projetor na mesma rede Wi-Fi.
2. A home mostra `Controle pelo celular: 192.168.0.42:8080`. Abra no navegador do celular e use **Adicionar à tela inicial**.
3. No primeiro comando, o projetor pergunta **Permitir depuração?**. Marque **Sempre permitir** e confirme com o controle físico.

### Aba Controle

D-pad (segure para repetir), OK, voltar, início, menu, volume, play/pausa, desligar, campo para digitar e atalhos dos apps da home.

### Comandos de voz

Toque no microfone e fale. Exemplos:

| Você fala | Acontece |
|---|---|
| "abrir Netflix", "coloca no Globoplay", "SBT mais", "Pluto TV" | abre o app (qualquer app instalado, não só os da home) |
| "aumentar volume", "abaixa o volume 5 vezes", "mudo" | volume |
| "pausar", "continuar", "play" | play/pausa |
| "voltar", "tela inicial", "menu" | navegação |
| "descer três vezes", "direita", "ok" | move o foco |
| "pesquisar louvor da manhã" | digita no campo em foco e confirma |
| "digitar minhasenha123" | só digita |
| "desligar" | botão de energia |

**Sobre o microfone:** o navegador só libera o microfone em página segura (https), e o controle roda em `http://IP:8080`. Por isso o botão abre um campo onde você toca no **microfone do teclado** (Gboard) e fala. Funciona em qualquer celular.

Para o microfone direto, sem teclado (Chrome no Android, uma vez só):
1. Abra `chrome://flags/#unsafely-treat-insecure-origin-as-secure`
2. Escreva `http://192.168.0.42:8080` (o endereço que aparece na home) e escolha **Enabled**
3. Toque em **Relaunch**

### Aba Organizar home

- Segure o **≡** e arraste para mudar a ordem: a home do projetor muda na hora.
- **×** tira o app da home. **+ Adicionar** coloca qualquer app instalado (até 12).
- **Voltar à ordem padrão** desfaz tudo.

> Segurança: qualquer pessoa na sua rede Wi-Fi consegue abrir o controle. Em casa tudo bem; para uma rede compartilhada dá para adicionar um PIN.

---

## Sobre cada app

- **Netflix**: a versão de TV (`com.netflix.ninja`) é a melhor no controle remoto. A qualidade depende do DRM: esses projetores geralmente têm Widevine **L3**, e com L3 a Netflix fica em SD em qualquer versão. Confira com o app **DRM Info**. O launcher tenta a versão TV e depois a de celular.
- **YouTube**: prefere o **SmartTube** (open source, feito para TV, sem anúncios; baixe só do GitHub oficial `yuliskov/SmartTube`). Sem ele, abre o YouTube TV ou o de celular.
- **Pluto TV, Globoplay, Stremio, SBT+**: achados pelo pacote ou pelo nome do app.

Os favoritos padrão ficam em `app/src/main/java/br/leve/launcher/Config.kt`.

## Atalhos do projetor (HDMI e foco)

São apps do fabricante e o pacote muda a cada firmware. Abra o app de foco/HDMI no projetor e rode:

```
adb -s IP:5555 shell dumpsys activity activities | find "topResumedActivity"
```

(`grep` no Linux/Mac). Coloque o pacote em `Config.hdmiPackages` / `Config.focusPackages` e faça um commit: o Actions gera o APK novo. Até lá, esses botões abrem as Configurações.

## Por que é leve

- Sem AndroidX, sem Compose, sem bibliotecas de imagem. Só o cliente ADB (`dadb`).
- Views nativas, sem papel de parede nem animação de janela, R8 ligado.
- Servidor HTTP próprio de ~200 linhas num serviço em primeiro plano: o controle funciona com qualquer app aberto.

## Sobre a chave de assinatura

A chave que assina os APKs fica só nos **Secrets** do repositório (Settings › Secrets and variables › Actions):

| Secret | Conteúdo |
|---|---|
| `KEYSTORE_BASE64` | o arquivo `.jks` em base64 |
| `KEYSTORE_PASSWORD` | senha do arquivo |
| `KEY_ALIAS` | `lancador` |
| `KEY_PASSWORD` | senha da chave |

Sem eles o Actions para com um aviso. Para compilar no PC, o APK sai com a chave de debug, e para instalar por cima de uma versão do Releases é preciso desinstalar antes.

Guarde uma cópia do `.jks` e das senhas fora do GitHub. Se a chave se perder, as próximas versões não instalam por cima das atuais.

## Ideias para as próximas versões

- PIN no controle do celular
- Botões de foco e trapézio direto no celular
- Busca dentro do app ("pesquisar no YouTube ...")
- Tema claro/escuro e papel de parede opcional
