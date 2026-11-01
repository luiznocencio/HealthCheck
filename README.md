# HealthCheck 💊

App Android de **lembretes/alarmes de remédios** com **compartilhamento entre usuários**:
você pode cadastrar remédios para outra pessoa (ex.: pai, mãe, avó) e os alarmes tocam
**no celular dela**. Você também acompanha se ela tomou ou não cada dose.

## Funcionalidades

- **Alarmes de remédio** seguindo o padrão do tratamento:
  - quantas vezes por dia (1x a 6x, com horários distribuídos automaticamente ou ajustados um a um);
  - quais dias da semana (todos os dias, segunda a sexta ou dias específicos);
  - duração do tratamento em dias (ou uso contínuo);
  - dose e observações (ex.: "1 comprimido", "tomar após o almoço").
- **Notificação com som de alarme** e botões **Tomei**, **Adiar 10 min** e **Pular**.
- Alarmes continuam funcionando **offline** e são recriados ao **reiniciar o celular**.
- Aba **Hoje** com todas as doses do dia e o status (pendente, atrasado, tomado, pulado).
- **Compartilhamento entre usuários**:
  1. Cada pessoa cria uma conta (nome, e-mail e senha).
  2. Na aba **Pessoas**, você envia um convite pelo e-mail da outra pessoa.
  3. Ela aceita o convite no app dela.
  4. Você abre o nome dela e cadastra os remédios → em até ~15 minutos (ou na hora, ao abrir o app)
     os remédios chegam no celular dela, ela recebe um aviso "Novo remédio para você"
     e os alarmes passam a tocar lá.
  5. Você vê as doses de hoje dela (tomou / pulou / atrasada).

## Arquitetura

| Parte | Tecnologia |
|---|---|
| App | Kotlin + Jetpack Compose (Material 3), minSdk 26 (Android 8.0) |
| Alarmes | `AlarmManager.setAlarmClock` (dispara mesmo no modo soneca) + notificações |
| Sincronização | WorkManager (a cada 15 min e ao abrir o app) |
| Backend | [Supabase](https://supabase.com) (Auth + Postgres com Row Level Security) |

Estrutura:

```
app/src/main/java/com/healthcheck/app/
├── data/      # modelos, cliente Supabase, repositório, cache local
├── alarm/     # agendamento de alarmes, notificações, receivers
├── sync/      # SyncWorker (baixa remédios e reagenda alarmes)
└── ui/        # telas Compose (login, hoje, remédios, pessoas, cadastro)
supabase/schema.sql   # tabelas, permissões (RLS) e funções do banco
```

## Como configurar

O backend já está criado: projeto Supabase **healthcheck** (plano gratuito, região São Paulo),
`https://fdjfgswjpitlzavbniez.supabase.co`. A URL e a chave pública (*anon*) já estão no
`app/build.gradle.kts`, então o APK gerado pelo GitHub Actions já se conecta ao banco.

> A chave *anon* é pública por natureza (vai dentro do app). A segurança é garantida pelas
> regras de RLS do [`supabase/schema.sql`](supabase/schema.sql): cada pessoa só vê os próprios
> dados e os de quem aceitou o vínculo de cuidado.

### Gerar o APK

1. No GitHub, abra **Actions → Build APK** (roda sozinho a cada push, ou use **Run workflow**).
2. Ao terminar, baixe o artefato **HealthCheck-apk** (um `.zip` com o `.apk` dentro).
3. Instale nos celulares (é preciso permitir "instalar apps desconhecidos").

Todos os APKs são assinados com a mesma chave (`app/release.keystore`) e cada build do GitHub
tem um número de versão maior. Por isso uma versão nova **instala por cima** da anterior,
sem desinstalar e sem perder nada. Quem instalou um APK antigo (anterior a essa chave)
precisa desinstalar uma única vez.

Para gerar no computador (Android Studio ou Android SDK + JDK 17): `./gradlew assembleRelease`.

### Login com Google (configuração única)

O app usa o login do Google pelo navegador. Para ativar:

1. No [Google Cloud Console](https://console.cloud.google.com/apis/credentials):
   - configure a **Tela de consentimento OAuth** (tipo *Externo*, nome "HealthCheck", seu e-mail);
   - em **Credenciais → Criar credenciais → ID do cliente OAuth**, escolha **Aplicativo da Web**;
   - em **URIs de redirecionamento autorizados**, adicione
     `https://fdjfgswjpitlzavbniez.supabase.co/auth/v1/callback`;
   - copie o **ID do cliente** e a **Chave secreta do cliente**.
2. No Supabase, em **Authentication → Sign In / Providers → Google**: ative, cole o ID e a chave secreta e salve.
3. No Supabase, em **Authentication → URL Configuration → Redirect URLs**, adicione
   `healthcheck://login-callback`.
4. (Opcional) Em **Authentication → Sign In / Providers → Email**, desative **Confirm email**
   para quem usa e-mail e senha poder entrar logo após criar a conta.

## Dicas para os alarmes tocarem sempre

- Ao abrir o app, permita **Notificações** e **Alarmes e lembretes** (o app mostra um aviso se faltar).
- Em alguns celulares (Xiaomi, Samsung, Motorola…), desative a **otimização de bateria** para o
  HealthCheck em *Configurações → Apps → HealthCheck → Bateria → Sem restrições*.

## Próximos passos possíveis

- Push instantâneo (Firebase Cloud Messaging) para o remédio chegar no outro celular no mesmo segundo.
- Avisar o cuidador quando uma dose ficar atrasada.
- Controle de estoque (quantos comprimidos restam).
- Chave de assinatura guardada fora do repositório para publicar na Play Store.
