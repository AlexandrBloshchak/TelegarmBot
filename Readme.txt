## 2. Руководство разработчика

### 2.1 Экспорт базы MySQL в `db/schema.sql`

```bash
# Создаём папку, если её нет
mkdir -p db

# Выгружаем ТОЛЬКО структуру (DDL)
mysqldump \
  -u root -p \
  --databases telegram_bot \
  --no-data --skip-comments --compact \
  > db/schema.sql
```

> Если база живёт в Docker‑контейнере:
>
> ```bash
> docker exec <container> mysqldump -u root -p$MYSQL_ROOT_PASSWORD \
>   --databases telegram_bot --no-data --skip-comments --compact \
>   > db/schema.sql
> ```

### 2.2 Настройка параметров приложения

Файл `src/main/resources/application.yml` (или `application.properties`). Замени placeholder‑ы на свои значения.

```yaml
authorbot:                       # кастомный namespace
  username: MyQuizBot           # <— название бота (username без @)
  token: ${BOT_TOKEN}           # <— токен из @BotFather

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/telegram_bot?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root

# рекомендовано: пароли и токен хранить в переменных окружения
```

<details>
<summary>Пример override‑файла</summary>
Создайте `application-secret.yml`, добавьте его в `.gitignore`.

```yaml
authorbot:
  token: 123456:ABC-DEF...

spring:
  datasource:
    password: superSecret
```

</details>

### 2.3 Быстрый запуск через Docker Compose

`docker-compose.yml` (фрагмент):

```yaml
version: "3.9"
services:
  db:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: telegram_bot
      MYSQL_ROOT_PASSWORD: root
    volumes:
      - ./db/schema.sql:/docker-entrypoint-initdb.d/00_schema.sql:ro
    ports:
      - "3306:3306"

  bot:
    build: .
    environment:
      BOT_TOKEN: ${BOT_TOKEN}
    depends_on:
      - db
```

Запускайте `docker compose up -d` — контейнер `db` применит `schema.sql`, а контейнер `bot` стартует с указанным токеном.

### 2.4 Git‑правила

```gitignore
# Дамп структуры — ОК
!db/schema.sql

# Остальные дампы и секреты — игнорируем
*.sql
.env*
application-secret.yml
```

