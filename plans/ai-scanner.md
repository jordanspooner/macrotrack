# AI Food / Label Scanner

An optional AI vision mode for the Label scanner. When the user supplies an OpenRouter API key and enables AI mode, the app captures a single photo and calls an OpenRouter vision model that returns the food name, brand, nutrition per 100g/ml, and serving size as structured JSON. When AI mode is off, the existing live ML Kit OCR flow is used unchanged.

## Decisions

- **Default model:** `google/gemma-4-26b-a4b-it:free`
  - Multimodal (image + text -> text), free tier, supports `response_format` strict structured outputs.
  - Selected over `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`, which supports image input and tool calling but not structured outputs, requiring fragile local tool-call parsing.
  - Nemotron is not a v1 fallback; it can be evaluated later. Free-model availability and rate limits change, so the UI must handle temporary unavailability gracefully.
- **No serving-unit field.** Values are requested per 100g/ml and the app keeps its existing numeric portion model (`portionG`, displayed as `g/ml`).
- **One image, one result.** No rolling `LabelConsensus`; AI scans are capture-on-demand.
- **Strict labels, approximate meals.** A `Label / Meal` selector (default `Label`) drives the prompt. Label results are validated strictly; Meal results are marked estimates and can be saved with an explicit "AI estimate" warning.
- **Secure key storage.** The OpenRouter key is stored with Android Keystore-backed AES-GCM encryption, never plain DataStore, BuildConfig, or SavedState.

## Settings

New non-secret DataStore keys (in `SettingsKeys`):

- `ai_label_scanner_enabled: Boolean` (default `false`)

New secure credential store (`data/security/OpenRouterCredentialStore`):

- `save(apiKey: String)`, `read(): String?`, `clear()`
- AES-GCM key in Android Keystore; ciphertext+IV stored separately (e.g. its own DataStore file or encrypted file)
- The raw key is held in memory only while building a request

Settings UI additions (`SettingsScreen` / `SettingsViewModel` / `SettingsUiState`):

- "AI label scanner" `CollapsibleSectionCard`
- API key `OutlinedTextField` (masked, `Password` visual transformation)
- Show only "Key configured" / "Key not set" — never the stored value
- Actions: Save key, Clear key
- Toggle is disabled until a key is configured
- Privacy/cost copy: photos are sent to OpenRouter and billed against the user's key

Security:

- Exclude credential storage from `backup_rules.xml` / `data_extraction_rules.xml` (the app already backs up all files/databases/sharedprefs)
- Never log the key, the image, or the raw model response
- No `BuildConfig` for secrets

## Scanner UX

- Keep the existing `Label` tab in `AddScreen`.
- AI disabled: existing `LabelScanScreen` (live ML Kit OCR) unchanged.
- AI enabled: new `AiLabelScanScreen` using CameraX `ImageCapture` (not per-frame `ImageAnalysis`).
  - `Label` / `Meal` selector, default `Label`
  - "Analyze" button captures one photo, converts to a rotated, compressed JPEG, encodes it as a base64 data URL, then closes the `ImageProxy` immediately
  - States: `Ready`, `Analyzing`, `Result`, `Error` (with Retake / Retry)
  - Image is never persisted
- Result view shows name, brand, serving size, kcal, and macros.
- "Use this result" opens the existing Quick Add form for review/edit; nothing is saved automatically.

## API Contract

Endpoint: `POST https://openrouter.ai/api/v1/chat/completions`

Request:

- `model`: `google/gemma-4-26b-a4b-it:free`
- `messages`: system/user prompt + image as an `image_url` base64 data URL
- `response_format`: `json_schema` (strict), plus `provider.require_parameters = true` to pin endpoints that support structured outputs
- `temperature`: low (e.g. 0.1), bounded `max_tokens`, `stream: false`
- Headers: `Authorization: Bearer <key>`, `Content-Type: application/json`

Response schema (all keys required; values may be `null` when not readable; never invent values):

```json
{
  "name": "Example Food",
  "brand": "Example Brand",
  "kcal_per_100g": 250,
  "protein_g_per_100g": 12.5,
  "carbs_g_per_100g": 30,
  "fat_g_per_100g": 8,
  "serving_size": 40
}
```

Label prompt (strict):

- Read only the visible packaging and nutrition label
- Prefer the per-100g/ml column
- Convert per-serving values only when the serving amount is explicitly visible
- Do not guess missing label nutrition values; return `null` for absent/unreadable values

Meal prompt (approximate):

- Identify the photographed food or combined meal (one combined entry for v1)
- Estimate per-100g/ml nutrition and an approximate serving size
- Guessing is allowed; result is surfaced to the user as an AI estimate

## Domain & Validation

- Extend `ParsedNutritionLabel` with `name`, `brand`, and transient `isEstimate` (in `domain/parser/LabelParser.kt`)
- Map the OpenRouter DTO into `ParsedNutritionLabel`
- Update `AddViewModel.onLabelParsed()` so AI name/brand populate `QuickAddDraft` instead of the hardcoded `"Scanned food"`; carry `isEstimate` through to the draft
- New `domain/validation/AiLabelValidator`:
  - Non-negative, finite values
  - kcal/macro plausibility bounds (reuse existing bounds)
  - Macro total <= 100g
  - Serving size within bounds
  - kcal/macro consistency (reuse `NutritionValidator` conventions)
- Derive kcal from macros when kcal is absent but all three macros are present; mark derived
- Label results: reject/hard-fail on invalid values
- Meal estimates: allow saving with a clear "AI estimate" warning; still reject impossible values

## Data Layer

New `data/remote/OpenRouterLabelRepository` + `OpenRouterLabelRepositoryImpl`:

- Follows the `FoodSourceCatalogRepositoryImpl` pattern (OkHttp + kotlinx-serialization, `withContext(Dispatchers.IO)`, `Result<T>`)
- `@Serializable` request/response DTOs in `data/remote/dto/`
- `@Singleton`; bound in `RepositoryModule.kt` (or provided in `NetworkModule`)
- Error mapping for: missing tool call / malformed body, invalid key (401), rate limit / free-tier unavailability (429), timeouts, 5xx, unsupported model

No changes to `FoodItem`, Room, or the log-entry model — name, brand, portion, and per-100g macros already exist.

## Files

New:

- `ui/add/AiLabelScanScreen.kt`
- `ui/add/AiLabelScanViewModel.kt`
- `domain/validation/AiLabelValidator.kt`
- `data/remote/OpenRouterLabelRepository.kt`
- `data/remote/OpenRouterLabelRepositoryImpl.kt`
- `data/remote/dto/OpenRouterDto.kt`
- `data/security/OpenRouterCredentialStore.kt`

Modified:

- `ui/add/AddScreen.kt` — route to `AiLabelScanScreen` when AI enabled
- `ui/add/AddViewModel.kt` — AI name/brand + `isEstimate` into `QuickAddDraft`
- `domain/parser/LabelParser.kt` — `name`/`brand`/`isEstimate` on `ParsedNutritionLabel`
- `ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsUiState.kt`
- `data/repository/SettingsRepository.kt`, `SettingsRepositoryImpl.kt`
- `data/local/datastore/SettingsDataStore.kt`
- `di/RepositoryModule.kt` (or `NetworkModule.kt`)
- `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`
- `README.md` / `HANDOVER.md`

## Testing

- DTO serialization and response mapping
- `AiLabelValidator`: missing, negative, implausible, inconsistent values; kcal derivation
- Label vs Meal prompt behavior
- `AddViewModel`: name/brand/serving/macros reach `QuickAddDraft`; `isEstimate` propagation
- Settings: enable/disable, save/clear key, toggle gating
- MockWebServer (new test dep): success, invalid key, rate limit, timeout, malformed JSON, unsupported model
- Manual smoke tests against the real free Gemma endpoint: clear label, blurry label, per-serving-only label, multilingual label, meal photo, missing fields, offline, invalid credentials