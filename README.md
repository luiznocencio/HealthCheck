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

### 1. Criar o backend (Supabase — plano gratuito serve)

1. Crie um projeto em <https://supabase.com>.
2. Abra **SQL Editor**, cole todo o conteúdo de [`supabase/schema.sql`](supabase/schema.sql) e execute.
3. Em **Authentication → Sign In / Providers → Email**, recomendo **desativar "Confirm email"**
   para que a pessoa possa entrar logo após criar a conta (opcional).
4. Em **Project Settings → API**, copie a **Project URL** e a chave **anon / publishable**.

> A chave *anon* é pública por natureza (vai dentro do app). A segurança é garantida pelas
> regras de RLS do `schema.sql`: cada pessoa só vê os próprios dados e os de quem
> aceitou o vínculo de cuidado.

### 2. Gerar o APK pelo GitHub (sem instalar nada)

1. No GitHub, vá em **Settings → Secrets and variables → Actions → New repository secret** e crie:
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY`
2. Vá em **Actions → Build APK → Run workflow** (ou faça um push).
3. Ao terminar, baixe o artefato **HealthCheck-apk** (é um `.zip` com o `.apk` dentro).
4. Envie o APK para os celulares e instale (é preciso permitir "instalar apps desconhecidos").

### 2b. (Alternativa) Gerar o APK no computador

Requer Android Studio (ou Android SDK + JDK 17):

```bash
cp local.properties.example local.properties   # e preencha os valores
./gradlew assembleRelease
# APK em app/build/outputs/apk/release/app-release.apk
```

## Dicas para os alarmes tocarem sempre

- Ao abrir o app, permita **Notificações** e **Alarmes e lembretes** (o app mostra um aviso se faltar).
- Em alguns celulares (Xiaomi, Samsung, Motorola…), desative a **otimização de bateria** para o
  HealthCheck em *Configurações → Apps → HealthCheck → Bateria → Sem restrições*.

## Próximos passos possíveis

- Push instantâneo (Firebase Cloud Messaging) para o remédio chegar no outro celular no mesmo segundo.
- Avisar o cuidador quando uma dose ficar atrasada.
- Controle de estoque (quantos comprimidos restam).
- Assinatura com chave própria para publicar na Play Store.
