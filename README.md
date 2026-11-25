# RenewIQ local development

## One-command Docker startup

Install and start Docker Desktop with Linux containers (Compose v2 with `--wait` support), then run this from the project root in PowerShell:

```powershell
.\start-docker.ps1
```

Open **http://localhost:8080**. The launcher creates an ignored `.env` with randomly generated MySQL credentials on first use, builds the frontend/backend images, starts MySQL, waits for health checks, and returns when the app is ready. No locally installed Java, Maven, Node or MySQL is required. It preserves an existing `.env`; if you already created one, add `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD` before running. For a different port add `APP_PORT=8085` to `.env`. After initial setup, `docker compose up --build -d --wait` is also supported.

The frontend runs behind Nginx, which serves Angular routes and proxies `/api` to Spring Boot. The browser uses the same host for frontend, session cookies, CSRF and API calls. MySQL and the backend have no published host ports. Flyway applies V1–V5 to the container database automatically. MySQL uses the `mysql_data` named volume, retained across container restarts/rebuilds. This is a separate database from any existing local MySQL installation; local accounts/subscriptions are not imported automatically. Do not change the generated MySQL credentials after the volume is initialized without also updating the database account.

Optional AI and SMTP settings belong in `.env` (see `.env.example`), then rerun the launcher to apply them. The basic app starts without AI/SMTP credentials; AI answers and real emails require their respective credentials. Do not commit `.env`. The default setup is local HTTP; an HTTPS deployment needs a TLS reverse proxy and `SESSION_COOKIE_SECURE=true`.

```powershell
docker compose logs -f            # View service logs
docker compose ps                 # Check readiness
docker compose down               # Stop; retain database data
```

To use the app from another device on your trusted Wi-Fi, open `http://<your-computer-LAN-IP>:8080` and allow that port through your local firewall if needed. No API URL edits are required. Desktop and mobile use the same account data when connected to the same running stack. Avoid `docker compose down -v` unless you intend to permanently delete the container database.

Docker configuration has been statically checked in this workspace. Docker Desktop is not installed here, so image builds, container health checks and live MySQL migrations still require a Docker-enabled machine for end-to-end verification.

## Responsive interface

The existing theme, components and workflows are preserved. Navigation changes from a sidebar to a compact top layout at tablet sizes. Subscription and payment tables become labeled stacked rows on phones, with all actions retained. Forms scroll within short viewports; charts keep their own scroll area. Touch targets, keyboard focus, text wrapping, safe-area spacing, and the assistant composer adapt to smaller displays. Both light and dark themes are supported.

The dashboard, subscriptions, history and assistant were checked with isolated sample data at 320, 390, 768, 1024 and 1440 CSS pixels, including a long subscription name. This covers representative phone/tablet/desktop layouts, not every physical device or software keyboard.

Stack: Angular 18, Java 17+, Spring Boot 3.3, MySQL, and Groq (OpenAI-compatible API) for assistant generation.

## Run

Start MySQL. Database credentials are required environment variables; no password is stored in application configuration. `.env.example` lists the supported settings for reference (it is not automatically loaded).
In PowerShell, set credentials in the same terminal that starts the backend:

```powershell
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = '<your MySQL password>'
$env:GROQ_API_KEY = '<your Groq key>'
# Optional for a hosted database: set its JDBC URL with the host's required TLS settings.
# $env:SPRING_DATASOURCE_URL = 'jdbc:mysql://your-host:3306/renewiq_db?sslMode=VERIFY_IDENTITY'
# Optional: choose an available model that supports function/tool calling.
$env:AI_MODEL = 'openai/gpt-oss-20b'
Set-Location D:\Projects\renewiq\backend
mvn spring-boot:run
```

In another terminal:

```powershell
Set-Location D:\Projects\renewiq\frontend
npm start
```

Open http://localhost:4200. The Angular dev server proxies `/api` to backend port 8081 through `frontend/proxy.conf.json`; Docker uses the equivalent Nginx proxy. Restart the frontend dev server after updating this proxy configuration. Restart the backend after changing environment variables. Do not commit API keys. Missing credentials produce an actionable chat error. A valid provider account/model is still required to generate answers.

Alternatively, from the project root run `.\start-backend.ps1`. This checks port 8081 (or `SERVER_PORT` when set) before starting Maven and warns if the AI key is missing. Run one backend instance at a time. Stop it with Ctrl+C in the terminal that started it before restarting. The frontend currently targets port 8081, so retain that port for normal development.

For local startup, run `.\start-backend.ps1` from the project root. If database credentials are missing, the launcher prompts for your MySQL username and password (password input is hidden), then supplies them as environment variables for this terminal session. It does not write credentials to disk. For unattended startup use `.\start-backend.ps1 -NonInteractive`, which rejects missing credentials. Direct `mvn spring-boot:run` still requires the environment variables above to be set first; a different terminal does not inherit values set in an earlier one.

Flyway applies versioned migrations on startup and validates migration checksums; Hibernate only validates the schema (`ddl-auto=validate`). Flyway clean is disabled. Keep existing applied migration files unchanged and add a new migration for each schema change. Baseline version 0 supports the existing pre-Flyway database; for a new production database use an empty schema. Back up an existing database before migration. Store deployment credentials in the host's environment/secret manager and use a dedicated MySQL account with the permissions needed to run migrations. The default JDBC URL is for local development; set `SPRING_DATASOURCE_URL` for deployment.

If Maven ends with `Process terminated with exit code: 1`, that line is a summary. Read the earlier `APPLICATION FAILED TO START` section. For `Port 8081 was already in use`, stop the existing backend, then restart in the terminal containing your environment variables. Adding the API key to another terminal does not update an already running backend.

The launcher saves full startup output in `backend/startup.log` (ignored by Git) and prints relevant cause lines on failure. `Access denied for user '${SPRING_DATASOURCE_USERNAME}'` means the required environment variable was not set: start with `.\start-backend.ps1` and enter credentials when prompted instead of running Maven directly. An access-denied error for your actual MySQL username means MySQL rejected those credentials or permissions. The AI API key does not authenticate MySQL. Share only the diagnostic error lines when troubleshooting, never credentials.

## AI provider setup

Create a free Groq account and API key at https://console.groq.com/keys. Enter the key locally in the PowerShell terminal above; do not paste it into chat or commit it. The default endpoint is `https://api.groq.com/openai/v1`, with the tool-capable `openai/gpt-oss-20b` model. Groq's free tier has request/token quotas, not unlimited usage; check your account limits at https://console.groq.com/dashboard/limits. A single chat can use multiple API requests for tools.

Stop the existing backend with Ctrl+C and restart it in the terminal containing `GROQ_API_KEY`. The old OpenRouter key is intentionally ignored, so it cannot be sent to Groq. No database or frontend changes are required.

To use another OpenAI-compatible provider, set all three overrides together before restarting:

```powershell
$env:AI_API_KEY = '<key for the selected provider>'
$env:AI_BASE_URL = 'https://openrouter.ai/api/v1'
$env:AI_MODEL = 'anthropic/claude-sonnet-4.6'
```

`AI_API_KEY` takes precedence over `GROQ_API_KEY`; remove the override with `Remove-Item Env:AI_API_KEY -ErrorAction SilentlyContinue` when returning to Groq, and also clear `AI_BASE_URL` and `AI_MODEL`. For a local Ollama installation, use `http://localhost:11434/v1`, `AI_API_KEY=ollama`, and a locally downloaded tool-capable model ID. Local models require sufficient RAM and may be slower.

## Document RAG

On AI Assistant, use **+ â†’ Upload a document** inside the message composer to upload a UTF-8 `.txt` or `.md` subscription policy (maximum 200,000 bytes), then ask about it using terms from the document. Files appear as attachment chips and remain selected for follow-up questions. Use the chip's Ã— to detach a file while keeping it in your private library; use **+ â†’ Your documents** to reattach or delete it. New chat clears the selection without deleting files. Example: upload `netflix-policy.md` containing `Netflix cancellation requires thirty days notice.` and ask `What notice is required for Netflix cancellation?`

Only selected attachments are searched by the chat UI. Requests may pass `documentIds` to `/api/assistant/chat`: an empty array searches no documents; selected IDs are always filtered by the authenticated owner. Omitted IDs preserve compatibility with older clients by searching that user's library. This feature requires restarting the backend as well as refreshing the frontend.

A fictional sample is included at `examples/demo-policy.md`. Upload it and ask `What is the StreamBox cancellation notice?` Its expected answer is 30 days, with a document/chunk reference.

The backend persists extracted text in `knowledge_documents`. Flyway manages the tables through versioned migrations on startup, and Hibernate validates the resulting schema; existing subscriptions are retained. No SQL backup import is required. See `database/README.md` for the full schema, relationships, and feature wiring status. To inspect in MySQL:

```sql
USE renewiq_db;
SHOW TABLES;
SELECT id, name, uploaded_at FROM knowledge_documents;
```

Retrieval splits documents into 1,200-character chunks with 200-character overlap, ranks chunks by query keyword coverage, and supplies up to four matching excerpts to the model. This is **lexical RAG**, without embeddings or a vector database. It has limited synonym/paraphrase support and scans a small library (maximum 100 documents); it is not a production-scale semantic search system. TXT/Markdown only: PDF, OCR, and DOCX extraction are not implemented.

Chat replies show the actual retrieved passages under **Retrieved references**. These are evidence supplied to the model, not a guarantee that every passage supports the answer. The model is instructed to cite document IDs/chunk numbers, treat document text as untrusted data, and use database tools for live subscription amounts and statuses. Model-generated citations and prompt-injection resistance are best effort. Deleted documents are excluded from later retrieval; start a new chat to remove earlier document-derived answers from conversation history.

Relevant document excerpts and tool results are sent to the configured AI provider (Groq by default) during chat. Original uploaded files are not stored on disk; their decoded text is retained in MySQL until deletion. Existing assistant status changes still require the app's Confirm button.

## Saved conversations

The assistant now saves sent messages to MySQL automatically. **Saved chats** lists your conversations; reopen one to view and continue it. The browser remembers the last selected conversation per account and restores it on refresh. **New chat** starts a separate conversation without deleting earlier ones; it is created in the database when the first message is sent. Empty new chats are not created merely by clicking New chat.

Messages, chart snapshots, source excerpts, attachment names, error replies, and confirmation/dismissal states are retained in `chat_messages`; titles and ownership are in `chat_conversations`. Deleting a document removes it from future retrieval but does not erase excerpts or filenames already saved in chats. Charts in old messages remain snapshots, not automatically refreshed account totals. All history endpoints check the authenticated owner.

The server supplies the last 20 non-error messages (up to 2,000 characters each) as model context when continuing a conversation; full saved messages remain viewable. Request IDs prevent accidental duplicate submissions. The user message commits before contacting the AI provider, so a provider failure still leaves the sent message and a useful error in history. An interrupted server request is marked recoverable after ten minutes; the next turn records the interruption. Reloading during generation polls the saved conversation until its reply is available. In-memory sessions expire on server restart, but database conversations survive and are available after signing in again.

V2 adds the required metadata and request-tracking columns automatically on backend startup. Old browser-only chats cannot be recovered after they were lost; this update saves new conversations. Legacy `/api/assistant/chat` remains a stateless compatibility endpoint; the application now sends messages through `/api/assistant/conversations/{id}/messages`.

For a ready-to-upload fictional dataset and expected answers, see `examples/assistant-test-data.md` and `examples/assistant-test-prompts.md`.

## Assistant charts

Ask `Chart my monthly spending by category`, `Compare subscription costs in a chart`, `Graph my renewals over the next 30 days`, or `Visualize my 6-month spending estimate`. The model chooses a `show_chart` tool and optional category/window; Java computes all plotted values from the signed-in user's records. A reply can include up to three charts. Labels, INR units, an as-of date, hover/focus values, an exact-value table, and CSV export accompany each chart. Both themes are supported; charts scroll horizontally on small screens.

Charts use active subscriptions only. Category and subscription comparisons normalize costs to monthly equivalents. More than twelve comparison rows are grouped into the eleven largest plus Other, preserving the total. Renewal charts use up to twelve chronological bins and count each subscription's recorded next renewal once; they exclude overdue dates and do not generate later recurring charges. Projections show cumulative monthly equivalents starting this month with unchanged costs, not actual history or a payment schedule. Recorded payments are available on History; assistant charts do not yet use payment history. Document-derived arbitrary datasets and arbitrary chart code are not supported; the assistant must not invent their values. Natural-language interpretation and the written explanation still depend on model quality and provider availability.

## Accounts and privacy

Signup now writes a real `users` row with a BCrypt password hash. Emails are trimmed/lowercased and must be unique. Passwords require at least 8 characters and at most 72 UTF-8 bytes. Sign-in verifies the stored hash; it no longer accepts arbitrary passwords. The frontend removes the old `renewiq_user` localStorage identity and restores identity from `GET /api/auth/me` on refresh.

Authentication uses an HttpOnly session cookie, a 30-minute inactivity timeout, session ID rotation on login, and CSRF tokens on state-changing requests. Sessions are in server memory and expire on backend restart. The local frontend must use `http://localhost:4200` and backend `http://localhost:8081`; mixing localhost with 127.0.0.1 changes cookie/origin behavior. For HTTPS deployment set `SESSION_COOKIE_SECURE=true` and configure the deployment origins/URLs. Login rate limiting, email verification, and password reset are not implemented.

Every subscription list/read/update/delete/renew, dashboard summary, upcoming-renewal query, assistant data tool/status change, and document list/upload/delete/retrieval is scoped to the authenticated user. Ownership is assigned on the server; the API ignores client-supplied user objects. Foreign-owned IDs return 404. New accounts start with empty subscriptions and document libraries; automatic demo seeding has been removed.

Old subscriptions/documents with NULL `user_id` are retained but excluded from all account queries. They are not automatically assigned to the first signup because their owner cannot be verified. Before assigning one manually, verify the intended account and record ID in MySQL; only update that specific record. Do not assign all shared data to all users.

After updating, stop the old backend with Ctrl+C, start `.\start-backend.ps1` from the project root, restart `npm start` if needed, and refresh the browser. Register again if your earlier account existed only in localStorage. Accounts created through the former demo UI never had server passwords and cannot be migrated into authenticated accounts automatically.

Auth endpoints: `GET /api/auth/csrf`, `POST /api/auth/signup`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`. Browser requests include credentials and the `X-CSRF-TOKEN` header for writes. Get a fresh token after signup/login/logout because authentication rotates it. No passwords or session tokens are stored in localStorage.

Endpoints: `GET /api/knowledge`, multipart `POST /api/knowledge` with field `file`, `DELETE /api/knowledge/{id}`, and `POST /api/assistant/chat`.

## Verification

Run `mvn test` in backend and `npm run build` in frontend. Tests include complete HTTP signup/login/logout flows against an isolated H2 database; password hashing; normalized duplicate emails; invalid input; CSRF rotation; and cross-user subscription, dashboard, document, RAG, and assistant-action isolation. Retrieval tests also cover ranking, chunk coverage, and upload validation. Live provider calls require your credentials and credits.

## Next milestones

1. Add password reset, email verification, and login rate limiting before public deployment.
2. Extend saved chat management with search, rename, pagination, and explicit retention/deletion controls as usage grows.
3. Add PDF extraction (and OCR for scans), then embeddings and a vector index when lexical search is insufficient. Keep retrieval behind `KnowledgeService` so the chat/UI contract stays stable.
4. Extend renewal reminders with per-user timezone/preferences and delivery monitoring.
5. Keep future database changes in reviewed Flyway migrations (V1 now establishes the schema).

## Payment history

After paying an active subscription, click **Paid ✓** on Subscriptions and confirm the amount. This records the current per-cycle amount in INR and advances the renewal by one billing cycle, together in one database transaction. It does not charge a card, contact the provider, or automatically detect bank payments. Creating/editing a subscription or passing a renewal date does not create a payment. Past payments are not inferred or backfilled.

**History** lists only the signed-in user's recorded payments, newest first, with 20 records per page. Each record stores the subscription name, category, billing cycle, amount, currency, renewal date covered, and UTC recording time. The recording time is shown in the browser's local timezone. Editing or deleting the tracked subscription preserves these historical values. Payments cannot be edited. History provides a Delete button with permanent-deletion confirmation. DELETE /api/payments/{id} removes only a payment owned by the signed-in user; it does not undo a renewal or change the subscription. The list returns to the previous available page when the last record on a page is deleted.

`POST /api/subscriptions/{id}/renew` now requires a JSON body such as `{"expectedRenewalDate":"2026-11-24"}`. The server checks the authenticated owner, active status, and current renewal date under a database lock. A stale/repeated renewal returns 409 without creating another payment or advancing again. `GET /api/payments?page=0` returns owner-scoped `items`, `page`, `totalPages`, and `totalElements`. Flyway V4 adds the payments table on backend startup. Restart the backend and refresh the frontend after updating.

## Renewal alerts

The sidebar bell shows the signed-in user's active subscriptions renewing today through seven days ahead, ordered by renewal date. It refreshes immediately after subscription creation, editing, renewal or deletion, as well as every minute and when opened. Paused/cancelled subscriptions and overdue renewals are excluded. These are upcoming alerts, not an unread-message inbox; they disappear when the subscription is renewed, paused, cancelled, deleted, or leaves the date window. The bell works without an email provider.

When an active subscription is first added with a renewal date from today through the next 7 calendar days (inclusive), the backend immediately attempts an SMTP email to the account's signup address, after saving the subscription. Paused/cancelled, overdue and more distant subscriptions do not trigger this immediate email. Editing a subscription does not trigger another immediate email. SMTP must be enabled/configured as below; a failed email does not prevent saving the subscription.

The daily job continues to send 7-, 3-, and 1-day reminders at 09:00 Asia/Kolkata (9:00 AM IST). The same day's reminder is deduplicated if the immediate email already covered it. For example, adding YouTube due in 2 days sends immediately and then at 9:00 AM IST the next day, when renewal is 1 day away. Adding a subscription due tomorrow sends immediately, without another 1-day email later that same day. Adding one due in 5 days sends immediately, then at the 3- and 1-day thresholds.

The job also checks that day's 7/3/1 reminders at backend startup. The backend must remain running for scheduled delivery; missed reminder dates are not backfilled. Immediate emails that fail on a 7/3/1-day date can retry at that day's scheduled run or restart. Failed immediate emails on other dates are not automatically retried that day, but later 3/1-day reminders still run. Existing subscriptions are not retroactively given a new immediate email on upgrade.

Set these in the terminal that starts the backend, using your SMTP provider's credentials and an authorized sender address:

```powershell
$env:REMINDER_EMAIL_ENABLED = 'true'
$env:REMINDER_FROM = '<authorized sender email>'
$env:SMTP_HOST = '<SMTP hostname>'
$env:SMTP_PORT = '587'
$env:SMTP_USERNAME = '<SMTP username>'
$env:SMTP_PASSWORD = '<SMTP password or app password>'
# Optional (defaults shown):
$env:REMINDER_TIMEZONE = 'Asia/Kolkata'
$env:REMINDER_CRON = '0 0 9 * * *'
```

Restart the backend after setting these values. SMTP authentication and required STARTTLS are enabled by default; `SMTP_AUTH` and `SMTP_STARTTLS` can be set to `false` for a local test SMTP server. Emails default to disabled, so no real emails are sent until explicitly enabled. Enabled email requires an SMTP host and sender. SMTP passwords stay in environment variables; `.env.example` is a reference, not an automatically loaded file. Amounts use INR, matching the app's current currency.

Flyway V3 creates `renewal_email_deliveries`; V5 expands its allowed day offsets to 0–7 for immediate reminders. A successful SMTP submission is recorded per subscription, renewal date, and day offset. Database locking and a unique constraint suppress repeat submissions during normal reruns and concurrent job instances. Failures are logged without recipient/provider details. One failed email does not stop the other reminders. SMTP acceptance does not guarantee inbox delivery. A crash or database failure after SMTP acceptance but before the record commits can still produce a duplicate on retry.

Verify locally with `mvn test` and `npm run build`. To test actual delivery, use a test SMTP inbox and an active subscription due in 7, 3, or 1 days, then start the backend with email enabled. Restart again and confirm no repeat email. Live SMTP credentials are needed to verify real delivery.
