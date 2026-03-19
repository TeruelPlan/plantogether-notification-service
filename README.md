# Notification Service

> Service d'orchestration des notifications push et email

## Rôle dans l'architecture

Le Notification Service orchestre toutes les notifications destinées aux utilisateurs. Il consomme les événements
RabbitMQ émis par les autres microservices (trip, poll, expense, task, chat), résout les informations utilisateur depuis
le cache Redis ou l'API Keycloak Admin, puis envoie des notifications push (Firebase Cloud Messaging) et des emails. Le
service respecte la stratégie "zéro PII" en ne stockant que des UUIDs.

## Fonctionnalités

- Consommation asynchrone des événements RabbitMQ
- Résolution des profils utilisateur (UUID → cache/Keycloak)
- Envoi de notifications push via Firebase Cloud Messaging (FCM)
- Envoi d'emails via SMTP
- Préférences de notifications par voyage et par type
- Gestion des templates d'email multilingues
- Retry automatique pour les envois échoués
- Historique des notifications
- Silencing des notifications (Do Not Disturb)

## Types de notifications

| Événement           | Déclencheur                    | Push | Email | Paramètre                          |
|---------------------|--------------------------------|------|-------|------------------------------------|
| `PollCreated`       | Nouveau sondage de dates       | ✓    | ✓     | `notification.poll_created`        |
| `PollLocked`        | Résultat du sondage verrouillé | ✓    | ✗     | `notification.poll_locked`         |
| `ExpenseCreated`    | Nouvelle dépense               | ✓    | ✓     | `notification.expense_created`     |
| `TaskAssigned`      | Tâche assignée                 | ✓    | ✓     | `notification.task_assigned`       |
| `DeadlineReminder`  | Rappel 24h avant deadline      | ✓    | ✗     | `notification.deadline_reminder`   |
| `MessageSent`       | Nouveau message chat           | ✓    | ✗     | `notification.message_sent`        |
| `MemberJoined`      | Nouveau membre du voyage       | ✓    | ✓     | `notification.member_joined`       |
| `TripStatusChanged` | Changement d'état du voyage    | ✓    | ✗     | `notification.trip_status_changed` |

## Endpoints REST

| Méthode | Endpoint                                  | Description                         |
|---------|-------------------------------------------|-------------------------------------|
| PUT     | `/api/notifications/preferences`          | Mettre à jour les préférences       |
| GET     | `/api/notifications/preferences/{tripId}` | Récupérer les préférences           |
| GET     | `/api/notifications/history`              | Historique des notifications reçues |
| POST    | `/api/notifications/dnd`                  | Activer Ne pas déranger (DND)       |
| DELETE  | `/api/notifications/dnd`                  | Désactiver DND                      |
| GET     | `/api/notifications/fcm-token`            | Récupérer le token FCM              |
| POST    | `/api/notifications/fcm-token`            | Enregistrer le token FCM            |

## Modèle de données

**NotificationPreference**

- `id` (UUID)
- `keycloak_id` (UUID) : utilisateur
- `trip_id` (UUID) : voyage (ou NULL pour préférences globales)
- `poll_created` (Boolean, default: true)
- `poll_locked` (Boolean, default: true)
- `expense_created` (Boolean, default: true)
- `task_assigned` (Boolean, default: true)
- `deadline_reminder` (Boolean, default: true)
- `message_sent` (Boolean, default: true)
- `member_joined` (Boolean, default: true)
- `trip_status_changed` (Boolean, default: true)
- `updated_at` (Timestamp)

**FCMToken**

- `id` (UUID)
- `keycloak_id` (UUID)
- `token` (String) : token Firebase Cloud Messaging
- `device_type` (ENUM: ANDROID, IOS, WEB)
- `created_at` (Timestamp)
- `updated_at` (Timestamp)

**DoNotDisturb**

- `keycloak_id` (UUID, primary key)
- `enabled` (Boolean)
- `start_time` (LocalTime) : début du DND (ex: 22:00)
- `end_time` (LocalTime) : fin du DND (ex: 08:00)
- `timezone` (String, default: UTC)

**NotificationLog**

- `id` (UUID)
- `keycloak_id` (UUID)
- `type` (ENUM: PUSH, EMAIL)
- `trip_id` (UUID, nullable)
- `event_type` (String) : ex "PollCreated"
- `sent_at` (Timestamp)
- `status` (ENUM: SUCCESS, FAILED, PENDING)
- `message` (String, nullable) : description d'erreur

## Événements (RabbitMQ)

**Consomme :**

- `TripCreated` → Notifier les organisateurs
- `PollCreated` → Notifier les participants
- `PollLocked` → Notifier les participants
- `ExpenseCreated` → Notifier les participants
- `TaskAssigned` → Notifier l'assigné
- `DeadlineReminder` → Notifier les responsables
- `MessageSent` → Notifier les autres participants
- `MemberJoined` → Notifier les membres du voyage
- `TripStatusChanged` → Notifier les participants

**Publie :**

- `NotificationSent` — Après envoi réussi
- `NotificationFailed` — Après échec et retry exhaustif

## Configuration

```yaml
server:
  port: 8087
  servlet:
    context-path: /
    
spring:
  application:
    name: plantogether-notification-service
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  datasource:
    url: jdbc:postgresql://postgres:5432/plantogether_notification
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
  redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}
  mail:
    host: ${MAIL_SMTP_HOST}
    port: ${MAIL_SMTP_PORT:587}
    username: ${MAIL_SMTP_USER}
    password: ${MAIL_SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true

keycloak:
  serverUrl: ${KEYCLOAK_SERVER_URL:http://keycloak:8080}
  realm: ${KEYCLOAK_REALM:plantogether}
  clientId: ${KEYCLOAK_CLIENT_ID}
  clientSecret: ${KEYCLOAK_CLIENT_SECRET}
  adminUsername: ${KEYCLOAK_ADMIN_USER}
  adminPassword: ${KEYCLOAK_ADMIN_PASSWORD}

firebase:
  credentialsPath: ${FIREBASE_CREDENTIALS_PATH:/config/firebase-admin-key.json}
  projectId: ${FIREBASE_PROJECT_ID}

notification:
  fcm:
    enabled: true
    batchSize: 100
  email:
    enabled: true
    from: noreply@plantogether.app
    replyTo: support@plantogether.app
  retry:
    maxAttempts: 3
    initialDelay: 5000  # ms
    maxDelay: 300000    # 5 minutes
```

## Lancer en local

```bash
# Prérequis : Docker Compose (infra), Java 21+, Maven 3.9+
# Prérequis : Fichier firebase-admin-key.json (Firebase Console)

# Option 1 : Maven
mvn spring-boot:run

# Option 2 : Docker
docker build -t plantogether-notification-service .
docker run -p 8087:8081 \
  -e KEYCLOAK_SERVER_URL=http://host.docker.internal:8080 \
  -e KEYCLOAK_ADMIN_USER=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e MAIL_SMTP_HOST=smtp.gmail.com \
  -e MAIL_SMTP_USER=your-email@gmail.com \
  -e MAIL_SMTP_PASSWORD=your-app-password \
  -e FIREBASE_PROJECT_ID=your-project-id \
  -v /path/to/firebase-admin-key.json:/config/firebase-admin-key.json \
  plantogether-notification-service
```

## Dépendances

- **Keycloak 24+** : résolution des profils utilisateur (Admin API)
- **PostgreSQL 16** : persistance des préférences et historique
- **RabbitMQ** : consommation d'événements
- **Redis** : cache des profils utilisateur
- **Firebase Cloud Messaging** : notifications push
- **Spring Boot 3.3.6** : framework web
- **Spring Mail** : envoi d'emails SMTP
- **FirebaseAdmin SDK** : API FCM
- **Thymeleaf** : templating d'emails

## Stratégie zéro PII

Le service ne stocke **jamais** de données personnelles d'utilisateurs :

1. Seuls les UUIDs Keycloak sont stockés
2. Les profils utilisateur (nom, email) sont résolus en temps réel depuis :
    - Cache Redis (TTL 24h) pour les appels fréquents
    - API Keycloak Admin pour les miss de cache
3. Les notifications contiennent au maximum : "Nouveau message dans Voyage 1" (pas de noms)

## Templates d'email

Les emails utilisent des templates Thymeleaf multilingues stockés en ressources.

Structure recommandée :

```
src/main/resources/email-templates/
├── poll-created.html
├── expense-created.html
├── task-assigned.html
└── (multilingues: poll-created_fr.html, poll-created_en.html, etc.)
```

## Gestion du DND (Do Not Disturb)

Les utilisateurs peuvent configurer leurs heures de silence. Les notifications push et email sont supprimées pendant les
plages DND, mais l'historique est conservé pour consultation.

## Notes de sécurité

- Les tokens FCM sont stockés en base et dans Redis
- Les credentials Firebase Admin sont externalisées (fichier montage/env vars)
- Les credentials SMTP doivent être des app-passwords (pas les vrais mots de passe)
- Les tokens Keycloak Admin API n'apparaissent jamais dans les logs
- Zéro PII : pas de noms, adresses email, téléphones en base
- Les timestamps de notifications ne sont pas personnels (pas de "23:47")

## Retry et résilience

En cas d'échec :

1. Premier retry : 5 secondes
2. Deuxième retry : 1 minute
3. Troisième retry : 5 minutes
4. Après 3 échecs : événement `NotificationFailed` publié
