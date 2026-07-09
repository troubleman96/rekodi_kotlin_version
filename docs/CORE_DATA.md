# core-data Module -- Data Layer

## Purpose

The `core-data` module implements the entire data persistence and storage layer of Rekodi. It contains the Room database for recording metadata, the DataStore preferences for application settings, the repository that wraps the DAO, and the Hilt DI module that wires everything together. This module depends on `core-common` (for `RekodiResult` and `RekodiDispatchers`) and on `core-ui` transitively through the app module, but it has no direct UI dependencies.

The module is structured as follows:

```
core-data/src/main/java/com/camelcreatives/rekodi/data/
  datastore/
    SettingsDataStore.kt       # DataStore preferences wrapper
  di/
    DataModule.kt              # Hilt @Module providing Room/DAO
  local/
    RekodiDatabase.kt          # Room @Database class
    dao/
      RecordingDao.kt          # Room @Dao with queries
    entity/
      RecordingEntity.kt       # Room @Entity data class
  repository/
    RecordingRepository.kt     # Injectable repository layer
```

---

## Database Schema

### RecordingEntity

**File:** `entity/RecordingEntity.kt`

```kotlin
package com.camelcreatives.rekodi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val durationMs: Long = 0,
    val fileSizeBytes: Long = 0,
    val resolution: String = "",
    val frameRate: Int = 30,
    val bitrate: Int = 0,
    val tapCount: Int = 0,
    val isFavorite: Boolean = false,
    val tags: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
```

**Line-by-line analysis:**

- **`@Entity(tableName = "recordings")`**: Room annotation that maps this data class to the SQLite table named `"recordings"`. Room uses the class properties as table columns. The class must be a `data class` (or have `equals`/`hashCode`/`toString`). Each instance becomes a row.

- **`val id: Long = 0`**: The primary key column in SQLite is `INTEGER` type. `autoGenerate = true` means Room will generate a unique ID on insert using `AUTOINCREMENT` in the underlying SQL. Default value `0` signals "not yet assigned" -- Room assigns the real ID during the first `insertRecording()` call and returns it as the function's return value. The column name in SQLite is `id`.

- **`val filePath: String`**: Stores the absolute file system path to the recording file (e.g., `/storage/emulated/0/Android/data/com.camelcreatives.rekodi/files/Movies/Rekodi_20260709_143052.mp4`). SQLite column type is `TEXT NOT NULL`. Cannot be null; every recording must have a file path.

- **`val fileName: String`**: Just the file name part (e.g., `"Rekodi_20260709_143052.mp4"`). Stored separately so the UI can display it without parsing `filePath`. Column type `TEXT NOT NULL`.

- **`val mimeType: String`**: MIME type string (e.g., `"video/mp4"`, `"audio/mp4"`). Used for filtering recordings into "video" and "audio" categories in `RecordingDao`. Column type `TEXT NOT NULL`.

- **`val durationMs: Long = 0`**: Duration in milliseconds. Defaults to `0` because the entity is inserted into the database before recording completes (the duration is unknown at insert time). After recording finishes, `RecordingForegroundService.stopRecording()` reads the elapsed time and updates this field. Column type `INTEGER`, default `0`.

- **`val fileSizeBytes: Long = 0`**: File size in bytes. Same lifecycle as `durationMs`: inserted as `0`, updated after recording completes. Column type `INTEGER`.

- **`val resolution: String = ""`**: Video resolution as a display string (e.g., `"1920x1080"`, `"1080x1920"`). Empty string for audio-only recordings. Column type `TEXT`, default `""`.

- **`val frameRate: Int = 30`**: Video frame rate (e.g., `30`, `60`). Default `30` fps. Column type `INTEGER`.

- **`val bitrate: Int = 0`**: Video encoding bitrate in bits per second (e.g., `8000000` for 8 Mbps). Column type `INTEGER`. Zero indicates unknown or not-yet-set.

- **`val tapCount: Int = 0`**: Number of taps detected during recording (via `TapDetectionAccessibilityService`). This is a gamification/analytics metric. Column type `INTEGER`.

- **`val isFavorite: Boolean = false`**: Whether the user has marked this recording as a favorite. Stored as `INTEGER` in SQLite (`0` = false, `1` = true). Used for the "favorites" filter query.

- **`val tags: String = ""`**: Comma-separated or space-separated user-defined tags (e.g., `"lecture,math,chapter3"`). Stored as `TEXT` to allow SQL `LIKE` searches. The UI provides tag editing in the recording detail screen.

- **`val notes: String = ""`**: Free-text user notes. Also `TEXT`, also included in search queries.

- **`val createdAt: Long = System.currentTimeMillis()`**: Unix timestamp (milliseconds since epoch) of when the recording was created (inserted). Defaults to current time at entity construction. Column type `INTEGER`.

### Table creation equivalent

Room generates the following SQL from this entity:

```sql
CREATE TABLE IF NOT EXISTS recordings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filePath TEXT NOT NULL,
    fileName TEXT NOT NULL,
    mimeType TEXT NOT NULL,
    durationMs INTEGER NOT NULL DEFAULT 0,
    fileSizeBytes INTEGER NOT NULL DEFAULT 0,
    resolution TEXT NOT NULL DEFAULT '',
    frameRate INTEGER NOT NULL DEFAULT 30,
    bitrate INTEGER NOT NULL DEFAULT 0,
    tapCount INTEGER NOT NULL DEFAULT 0,
    isFavorite INTEGER NOT NULL DEFAULT 0,
    tags TEXT NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT '',
    createdAt INTEGER NOT NULL
)
```

---

### RecordingDao

**File:** `dao/RecordingDao.kt`

```kotlin
package com.camelcreatives.rekodi.data.local.dao

import androidx.room.*
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getRecordingByIdFlow(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE mimeType LIKE 'video%' ORDER BY createdAt DESC")
    fun getVideoRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE mimeType LIKE 'audio%' ORDER BY createdAt DESC")
    fun getAudioRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE fileName LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchRecordings(query: String): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity): Long

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
}
```

**Line-by-line analysis of each method:**

#### `getAllRecordings()`

- **Annotation**: `@Query("SELECT * FROM recordings ORDER BY createdAt DESC")`
- **Return type**: `Flow<List<RecordingEntity>>` -- Room generates a reactive Flow that emits the full list whenever any row in the `recordings` table changes (insert, update, delete). Room tracks table invalidation automatically when using `Flow` as the return type. The initial emission happens on first collection.
- **Sort order**: `ORDER BY createdAt DESC` places the newest recording first. This is the default sort order used by the library screen.

#### `getRecordingById(id: Long)`

- **Annotation**: `@Query("SELECT * FROM recordings WHERE id = :id")` -- the `:id` syntax binds the method parameter to the SQL placeholder.
- **Return type**: `suspend fun` returning `RecordingEntity?`. Because this is a `suspend` function, Room runs the query on a background thread (using the coroutine dispatcher configured on the database builder, defaulting to `Dispatchers.IO`). Returns `null` if no recording with the given ID exists.
- **Use case**: One-shot fetch for editing or verifying a recording exists.

#### `getRecordingByIdFlow(id: Long)`

- **Return type**: `Flow<RecordingEntity?>` -- the Flow variant emits the current value on collection, then re-emits whenever that specific row changes. This is useful for the detail screen that needs to react to favorite toggles or tag edits.
- **Note**: Room's Flow implementation for `getRecordingByIdFlow` watches the entire `recordings` table (not just the specific row), so it re-emits on any table change. The filter is applied client-side via the SQL `WHERE` clause.

#### `getVideoRecordings()`

- **Query**: `"... WHERE mimeType LIKE 'video%' ..."`. Matches any MIME type starting with `"video"` (e.g., `"video/mp4"`, `"video/webm"`).
- **Use case**: The library screen's video filter tab.

#### `getAudioRecordings()`

- **Query**: `"... WHERE mimeType LIKE 'audio%' ..."`. Matches `"audio/mp4"`, `"audio/aac"`, etc.
- **Use case**: The library screen's audio filter tab.

#### `getFavoriteRecordings()`

- **Query**: `"... WHERE isFavorite = 1 ..."`. SQLite stores booleans as integers.
- **Use case**: The library screen's favorites filter tab.

#### `searchRecordings(query: String)`

- **Query**: `"... WHERE fileName LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ..."`
- The `||` operator is SQLite string concatenation. `'%' || :query || '%'` wraps the query string in `%` wildcards, making it a substring match.
- Searches across three columns: `fileName`, `tags`, and `notes`. This is intentionally broad.
- **Security**: Room uses parameterized queries (`:query`), which prevents SQL injection. The user-provided query string is never concatenated into the SQL string.
- **Case sensitivity**: By default, SQLite `LIKE` is case-insensitive for ASCII characters but case-sensitive for Unicode. For the expected use case (English filenames and tags), this is acceptable. A future enhancement could add `COLLATE NOCASE` or use FTS4.
- **Use case**: The library screen's search bar.

#### `insertRecording(recording: RecordingEntity): Long`

- **Annotation**: `@Insert(onConflict = OnConflictStrategy.REPLACE)`. `REPLACE` means if a row with the same primary key already exists, it will be deleted and replaced with the new entity. This is safe because the primary key is auto-generated (`0` on first insert), so conflicts only occur when explicitly inserting with a known ID.
- **Return value**: `Long` -- the auto-generated row ID. Room returns this directly from `SQLiteDatabase.insertOrThrow()`.
- **Suspend**: Runs on a background thread.

#### `updateRecording(recording: RecordingEntity)`

- **Annotation**: `@Update`. Room matches the entity by primary key and updates all other columns. If no row with the given ID exists, Room silently does nothing (it does not throw).
- **Suspend**: Runs on background thread.

#### `deleteRecording(recording: RecordingEntity)`

- **Annotation**: `@Delete`. Room deletes the row matching the entity's primary key. Returns `void` (does not report count of deleted rows).

#### `deleteRecordingById(id: Long)`

- **Annotation**: `@Query("DELETE FROM recordings WHERE id = :id")`. Direct SQL DELETE by primary key. This is preferred over `deleteRecording(entity)` when only the ID is available (avoids fetching the entity first).

---

### RekodiDatabase

**File:** `local/RekodiDatabase.kt`

```kotlin
package com.camelcreatives.rekodi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.camelcreatives.rekodi.data.local.dao.RecordingDao
import com.camelcreatives.rekodi.data.local.entity.RecordingEntity

@Database(entities = [RecordingEntity::class], version = 1, exportSchema = false)
abstract class RekodiDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}
```

**Analysis:**

- `@Database` annotation declares the Room database class. Parameters:
  - `entities = [RecordingEntity::class]`: The list of entity classes. Room generates table creation SQL for each. Currently one entity, but the array notation allows for future expansion (e.g., `UndoHistoryEntity`, `SessionEntity`).
  - `version = 1`: Current schema version. When entities change (column adds, renames, etc.), the version must be incremented and a `Migration` provided. Version 1 is the initial schema.
  - `exportSchema = false`: When `true`, Room writes JSON schema files to the build output for version control and migration testing. Set to `false` for simplicity in this project. A production app with CI migration testing would set this to `true` and configure `room.schemaLocation`.
- `abstract class RekodiDatabase : RoomDatabase()`: Room generates the implementation at compile time via KSP annotation processing.
- `abstract fun recordingDao(): RecordingDao`: Room generates the implementation, returning a thread-safe DAO instance. The DAO is a singleton within the database instance (confirmed by `@Singleton` in `DataModule`).

---

## DataStore

### RekodiSettings data class

**File:** `datastore/SettingsDataStore.kt`

```kotlin
data class RekodiSettings(
    val videoResolution: String = "Auto",
    val videoFps: Int = 30,
    val videoBitrate: String = "Medium",
    val orientationLock: String = "Auto",
    val countdownEnabled: Boolean = true,
    val stopOnLockScreen: Boolean = false,
    val audioSource: String = "Mic+Internal",
    val sampleRate: Int = 44100,
    val audioChannels: String = "Stereo",
    val noiseSuppression: Boolean = false,
    val bubbleEnabled: Boolean = true,
    val bubbleOpacity: Float = 0.4f,
    val bubbleHideRecording: Boolean = false,
    val zoomEnabled: Boolean = false,
    val zoomStyle: String = "Ripple",
    val zoomColor: String = "#E8A33D",
    val zoomSensitivity: Int = 5,
    val autoDeleteDays: Int = 0,
    val storageMaxCapMb: Int = 0,
    val darkMode: String = "System",
    val dynamicColor: Boolean = true,
    val language: String = "en",
    val tapCountEnabled: Boolean = true
)
```

**All 22 settings, explained:**

| # | Field | Type | Default | Purpose |
|---|-------|------|---------|---------|
| 1 | `videoResolution` | String | `"Auto"` | Output resolution for screen recording. Values: `"Auto"`, `"1080p"`, `"720p"`, `"480p"` |
| 2 | `videoFps` | Int | `30` | Frames per second target. Values: `24`, `30`, `60` |
| 3 | `videoBitrate` | String | `"Medium"` | Encoding bitrate preset. Values: `"Low"`, `"Medium"`, `"High"`, `"Ultra"` |
| 4 | `orientationLock` | String | `"Auto"` | Recording orientation. Values: `"Auto"`, `"Portrait"`, `"Landscape"` |
| 5 | `countdownEnabled` | Boolean | `true` | Show a 3-2-1 countdown before recording starts |
| 6 | `stopOnLockScreen` | Boolean | `false` | Automatically stop recording when the screen locks |
| 7 | `audioSource` | String | `"Mic+Internal"` | Audio capture source. Values: `"Mic"`, `"Internal"`, `"Mic+Internal"`, `"None"` |
| 8 | `sampleRate` | Int | `44100` | Audio sample rate in Hz. Values: `44100`, `48000` |
| 9 | `audioChannels` | String | `"Stereo"` | Audio channel configuration. Values: `"Mono"`, `"Stereo"` |
| 10 | `noiseSuppression` | Boolean | `false` | Enable noise suppression filter on audio input |
| 11 | `bubbleEnabled` | Boolean | `true` | Show the floating control bubble during recording |
| 12 | `bubbleOpacity` | Float | `0.4f` | Opacity of the floating bubble when idle (0.0-1.0) |
| 13 | `bubbleHideRecording` | Boolean | `false` | Automatically minimize the bubble when recording starts |
| 14 | `zoomEnabled` | Boolean | `false` | Enable the tap visualization overlay |
| 15 | `zoomStyle` | String | `"Ripple"` | Visualization style for taps. Values: `"Ripple"`, `"Dot"`, `"Magnifier"` |
| 16 | `zoomColor` | String | `"#E8A33D"` | Color of the tap visualization ripple, hex string |
| 17 | `zoomSensitivity` | Int | `5` | Sensitivity of tap detection (1-10 scale) |
| 18 | `autoDeleteDays` | Int | `0` | Automatically delete recordings older than N days. `0` = disabled |
| 19 | `storageMaxCapMb` | Int | `0` | Maximum storage cap for recordings in MB. `0` = unlimited |
| 20 | `darkMode` | String | `"System"` | Dark mode preference. Values: `"System"`, `"Light"`, `"Dark"` |
| 21 | `dynamicColor` | Boolean | `true` | Enable Material You dynamic color (Android 12+) |
| 22 | `language` | String | `"en"` | App language code (e.g., `"en"`, `"sw"` for Swahili) |
| 23 | `tapCountEnabled` | Boolean | `true` | Enable/disable the tap counter feature |

### SettingsDataStore class

```kotlin
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val VIDEO_RESOLUTION = stringPreferencesKey("video_resolution")
        // ... 23 preference keys total ...
    }

    val settings: Flow<RekodiSettings> = context.dataStore.data.map { prefs ->
        RekodiSettings(...)
    }

    suspend fun updateVideoResolution(value: String) {
        context.dataStore.edit { it[VIDEO_RESOLUTION] = value }
    }
    // ... 22 more update methods ...
}
```

**How DataStore Works:**

1. **File-based storage**: The `preferencesDataStore(name = "rekodi_settings")` extension property creates a singleton DataStore backed by a file at `rekodi_settings.preferences_pb` in the app's internal storage. The file uses Protocol Buffers format (not XML or JSON), which provides type safety, small file size, and atomic writes.

2. **Context extension**: Line 16: `private val Context.dataStore by preferencesDataStore(name = "rekodi_settings")`. The `by` delegate ensures a single DataStore instance per name per process. The `preferencesDataStore` extension is provided by `androidx.datastore:datastore-preferences`.

3. **Reading**: `context.dataStore.data` returns a `Flow<Preferences>`. The `.map` transformation converts the raw `Preferences` object into a `RekodiSettings` data class. Each property reads its key using `prefs[KEY] ?: defaultValue`. The `?:` operator provides the default when the key does not exist (first launch or after data clear). The Flow emits:
   - Immediately on first collection.
   - Whenever the preference file changes (by this process or another process, though single-process usage is the norm).
   - On application restart (re-read from disk).

4. **Writing**: Each `update*` method is a `suspend` function that calls `context.dataStore.edit { ... }`. The `edit` block receives a `MutablePreferences` object. Setting a value in the block schedules an atomic write. DataStore serializes writes: concurrent calls to `edit` are queued and executed sequentially.

5. **Preference Keys**: 23 companion object vals, each typed:
   - `stringPreferencesKey(...)` for String values.
   - `intPreferencesKey(...)` for Int.
   - `floatPreferencesKey(...)` for Float.
   - `booleanPreferencesKey(...)` for Boolean.
   
   The key string (e.g., `"video_resolution"`, `"bubble_opacity"`) is the name stored in the protocol buffer. Key names should be stable across app versions -- renaming a key loses the stored value.

6. **Thread safety**: DataStore reads and writes are already thread-safe. The `Flow` emits on `Dispatchers.IO` by default. The `edit` block runs on `Dispatchers.IO`.

7. **Why not SharedPreferences?**: DataStore offers:
   - Async API (no `apply()`/`commit()` confusion).
   - Flow-based reactive observation.
   - Type-safe keys.
   - No ANR risk from disk I/O on main thread.
   - Automatic migration from SharedPreferences.

---

## Repository Layer

### RecordingRepository

**File:** `repository/RecordingRepository.kt`

```kotlin
@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao
) {
    fun getAllRecordings(): Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()
    fun getVideoRecordings(): Flow<List<RecordingEntity>> = recordingDao.getVideoRecordings()
    fun getAudioRecordings(): Flow<List<RecordingEntity>> = recordingDao.getAudioRecordings()
    fun getFavoriteRecordings(): Flow<List<RecordingEntity>> = recordingDao.getFavoriteRecordings()
    fun searchRecordings(query: String): Flow<List<RecordingEntity>> = recordingDao.searchRecordings(query)
    fun getRecordingByIdFlow(id: Long): Flow<RecordingEntity?> = recordingDao.getRecordingByIdFlow(id)
    suspend fun getRecordingById(id: Long): RecordingEntity? = recordingDao.getRecordingById(id)
    suspend fun insertRecording(recording: RecordingEntity): Long = recordingDao.insertRecording(recording)
    suspend fun updateRecording(recording: RecordingEntity) = recordingDao.updateRecording(recording)
    suspend fun deleteRecording(recording: RecordingEntity) = recordingDao.deleteRecording(recording)
    suspend fun deleteRecordingById(id: Long) = recordingDao.deleteRecordingById(id)
}
```

**Design decisions:**

- The repository is currently a thin pass-through layer. Every method delegates directly to the DAO. This might seem unnecessary, but it provides:
  1. **Abstraction**: Feature modules depend on `RecordingRepository`, not on `RecordingDao`. If the data source changes (e.g., to a remote API), only the repository implementation changes.
  2. **Testability**: Feature modules can be tested with a fake repository instead of an in-memory Room database.
  3. **Single responsibility**: The repository is the single source of truth for recording data. Future cross-cutting concerns (cache invalidation, sync, logging) go here.

- `@Singleton` ensures one repository instance per application. The DAO it wraps is also a singleton (within the database), so multiple ViewModels share the same data source.

- Return types mirror the DAO:
  - `Flow<List<RecordingEntity>>` for reactive list queries.
  - `Flow<RecordingEntity?>` for reactive single-item queries.
  - `suspend fun` returning `RecordingEntity?` or `Long` for one-shot operations.

---

## DI: DataModule

**File:** `di/DataModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RekodiDatabase {
        return Room.databaseBuilder(
            context,
            RekodiDatabase::class.java,
            "rekodi_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideRecordingDao(database: RekodiDatabase): RecordingDao {
        return database.recordingDao()
    }
}
```

**Analysis:**

- `@Module`: Marks this object as a Hilt module that tells Hilt how to provide dependencies.
- `@InstallIn(SingletonComponent::class)`: This module's bindings are available application-wide. The `SingletonComponent` lives as long as the application process.
- `object DataModule`: Using a Kotlin `object` (not a class) is idiomatic for Hilt modules with only `@Provides` functions (no abstract bindings).
- `provideDatabase(@ApplicationContext context: Context)`: Room needs a `Context` to create the database. `@ApplicationContext` is a Hilt qualifier that injects the application-level `Context` (not an Activity or Service context). This prevents memory leaks -- an Activity context held by a singleton would prevent GC.
  - `Room.databaseBuilder(...)`: Builder pattern. Parameters:
    - `context`: Application context.
    - `RekodiDatabase::class.java`: The Room database class.
    - `"rekodi_database"`: The SQLite database filename (stored as `rekodi_database` in internal storage).
  - `.build()`: Creates the database instance. Room validates the schema on build if `@Database` includes `exportSchema = true`.
- `provideRecordingDao(database: RekodiDatabase)`: Gets the DAO from the database. The DAO is a stateless interface (Room generates the implementation), so it is safe to provide as a singleton.
- `@Singleton` on both providers: Hilt ensures only one `RekodiDatabase` instance and one `RecordingDao` instance exist. Database creation is expensive (schema validation, migration checks), so singleton scoping is important.

---

## Build Configuration

**File:** `core-data/build.gradle.kts`

```kotlin
plugins {
    id("camelcreatives.android.library")
    id("camelcreatives.android.hilt")
}

android {
    namespace = "com.camelcreatives.rekodi.data"
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
```

**Convention plugins used:**

1. `camelcreatives.android.library` (from `AndroidLibraryConventionPlugin.kt`):
   - Applies `com.android.library` and `org.jetbrains.kotlin.android`.
   - Sets `compileSdk = 35`, `minSdk = 26`.
   - Sets Java 17 source/target compatibility.
   - No Compose enabled.

2. `camelcreatives.android.hilt` (from `AndroidHiltConventionPlugin.kt`):
   - Applies `com.google.dagger.hilt.android` and `com.google.devtools.ksp`.
   - Adds `implementation(libs.hilt.android)` and `ksp(libs.hilt.compiler)`.
   - These are the Hilt annotations and annotation processor.

**Dependencies:**

- `project(":core:core-common")`: Depends on common utilities (dispatchers, result type).
- `libs.room.runtime`: Room runtime library (`androidx.room:room-runtime`). Provides `RoomDatabase`, `@Dao`, `@Entity`, etc.
- `libs.room.ktx`: Room Kotlin extensions (`androidx.room:room-ktx`). Provides `Flow` support and coroutine integration for DAO suspend functions.
- `ksp(libs.room.compiler)`: Room annotation processor using KSP (`androidx.room:room-compiler`). Generates `RecordingDaoImpl` and `RekodiDatabase_Impl` at compile time.
- `libs.datastore.preferences`: DataStore Preferences library (`androidx.datastore:datastore-preferences`). Provides `preferencesDataStore`, `PreferenceDataStore`, and typed key classes.
- `libs.coroutines.core`: Kotlin coroutines core library.
- `libs.coroutines.android`: Android-specific coroutine dispatchers (`Dispatchers.Main` support via `kotlinx-coroutines-android`).

The `Room.databaseBuilder` call does not use `.fallbackToDestructiveMigration()`. If the database version changes without a migration, the app will crash with a `MigrationException`. This is intentional: destructive migration would silently delete user recordings. Future updates must provide explicit `Migration` objects.
