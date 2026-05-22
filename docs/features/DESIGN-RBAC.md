# Admin RBAC

## Goal

The admin panel has two roles:

- **ADMIN**: full access to all admin functionality.
- **MODERATOR**: limited access to tournaments, tournament players, series, leagues, and Polemica competition lookup.

Moderators must not access user tools, users, card templates, card packs, economy settings, achievements, broadcasts, or marketplace moderation.

## Backend

### Roles

Roles are Spring Security authorities created from `User.builder().roles(...)`:

- `ROLE_ADMIN`
- `ROLE_MODERATOR`

Admin users remain in-memory Basic Auth users. The moderator account is optional and is only created when both moderator username and password are non-blank.

### Configuration

`AdminProperties.kt`:

```kotlin
@ConfigurationProperties(prefix = "app.admin")
data class AdminProperties(
    var username: String = "admin",
    var password: String = "defaultPassword123",
    var moderatorUsername: String = "",
    var moderatorPassword: String = "",
)
```

`application.yml`:

```yaml
app:
  admin:
    username: ${ADMIN_USERNAME:admin}
    password: ${ADMIN_PASSWORD:defaultPassword123}
    moderator-username: ${ADMIN_MODERATOR_USERNAME:}
    moderator-password: ${ADMIN_MODERATOR_PASSWORD:}
```

### User Details

`SecurityConfig.kt`:

```kotlin
@Bean
fun adminUserDetailsService(adminProperties: AdminProperties): UserDetailsService {
    val admin = User.builder()
        .username(adminProperties.username)
        .password("{noop}${adminProperties.password}")
        .roles("ADMIN")
        .build()
    val users = mutableListOf(admin)
    if (adminProperties.moderatorUsername.isNotBlank() && adminProperties.moderatorPassword.isNotBlank()) {
        users += User.builder()
            .username(adminProperties.moderatorUsername)
            .password("{noop}${adminProperties.moderatorPassword}")
            .roles("MODERATOR")
            .build()
    }
    return InMemoryUserDetailsManager(users)
}
```

### Access Rules

`SecurityConfig.kt`:

```kotlin
@Bean
@Order(1)
fun adminSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
        .securityMatcher("/api/v1/admin/**")
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { auth ->
            auth
                .requestMatchers(
                    "/api/v1/admin/me",
                    "/api/v1/admin/tournaments",
                    "/api/v1/admin/tournaments/**",
                    "/api/v1/admin/series/**",
                    "/api/v1/admin/leagues",
                    "/api/v1/admin/leagues/**",
                    "/api/v1/admin/polemica/**",
                ).hasAnyAuthority("ROLE_ADMIN", "ROLE_MODERATOR")
                .anyRequest().hasAuthority("ROLE_ADMIN")
        }
        .httpBasic { }
    return http.build()
}
```

Exact paths such as `/api/v1/admin/tournaments` and `/api/v1/admin/leagues` are included explicitly. Relying only on `/**` is not sufficient for the collection endpoints.

### Current Admin Endpoint

The frontend must not infer a role from the username. It asks the backend instead.

`MeAdminController.kt`:

```kotlin
@RestController
@RequestMapping("/api/v1/admin")
class MeAdminController {

    @GetMapping("/me")
    fun me(authentication: Authentication): AdminMeDto {
        val authorities = authentication.authorities.map { it.authority }.toSet()
        val role = when {
            "ROLE_ADMIN" in authorities -> AdminRole.ADMIN
            "ROLE_MODERATOR" in authorities -> AdminRole.MODERATOR
            else -> error("Unknown admin role")
        }
        return AdminMeDto(username = authentication.name, role = role)
    }
}
```

`AdminResponses.kt`:

```kotlin
enum class AdminRole {
    ADMIN,
    MODERATOR,
}

data class AdminMeDto(
    val username: String,
    val role: AdminRole,
)
```

## Permissions

### Moderator Allowed

| Path | Description |
|------|-------------|
| `/api/v1/admin/me` | Current admin identity and role |
| `/api/v1/admin/tournaments` | List/create tournaments |
| `/api/v1/admin/tournaments/**` | Tournament details, tournament players, player photos |
| `/api/v1/admin/series/**` | Series CRUD, roster assignment, sync, scoring, finalize |
| `/api/v1/admin/leagues` | List leagues |
| `/api/v1/admin/leagues/**` | League and series league configuration |
| `/api/v1/admin/polemica/**` | Polemica competition lookup |

### Moderator Forbidden

| Category | Examples |
|----------|----------|
| Users | `/api/v1/admin/users/**` |
| User tools | `/api/v1/admin/users/{telegramUserId}/give-cards`, `/open-pack`, `/give-fantiki`, `/take-fantiki` |
| Card templates | `/api/v1/admin/card-templates/**` |
| Card packs | `/api/v1/admin/card-packs/**` |
| Card skins | `/api/v1/admin/card-skins` |
| Economy config | `/api/v1/admin/economy-config/**` |
| Achievements | `/api/v1/admin/achievements/**`, `/api/v1/admin/achievement-statistics/**` |
| Marketplace moderation | `/api/v1/admin/marketplace/**` |
| Broadcasts | `/api/v1/admin/notifications/**` |

Note: `/api/v1/admin/series/{id}/players/{tournamentPlayerId}/unlist-marketplace` remains allowed because it is part of series/player operations and the agreed rule is full access to `/series/**`, including finalize.

## Frontend

### Auth State

`auth-context.ts`:

```typescript
export type AdminRole = 'admin' | 'moderator'

export interface AuthState {
  authed: boolean
  role: AdminRole | null
  roleLoading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}
```

### Role Fetching

`client.ts` exposes:

```typescript
export async function fetchMe(): Promise<AdminMeDto> {
  return apiJson<AdminMeDto>('/v1/admin/me')
}
```

`AuthProvider.tsx` calls `/v1/admin/me` after login or session restore. Backend values `ADMIN` and `MODERATOR` are normalized to frontend values `admin` and `moderator`.

### Menu Filtering

Moderators see only `Tournaments` in the sidebar. There is no standalone `/leagues` page; league operations are reachable from the series/tournament workflow.

`AdminLayout.tsx`:

```typescript
const menuItems =
  role === 'moderator'
    ? allMenuItems.filter((item) => item.key === '/tournaments')
    : roleLoading
      ? []
      : allMenuItems
```

### Role Badge

`AdminLayout.tsx` shows the current role in the header:

```typescript
<Tag color={role === 'admin' ? 'blue' : 'orange'}>
  {roleLoading ? 'Loading role' : role === 'admin' ? 'Admin' : 'Moderator'}
</Tag>
```

### Route Protection

No additional route-level frontend protection is required. If a moderator manually opens a hidden URL, backend authorization returns `403`. Sidebar filtering is a UX improvement only.

## Tests

### Backend

Add moderator credentials to `application-test.yml`:

```yaml
app:
  admin:
    username: admin
    password: test-admin-secret
    moderator-username: moderator
    moderator-password: test-moderator-secret
```

Add integration coverage in `AdminApiIntegrationTest.kt`:

- `/api/v1/admin/me` returns `ADMIN` for admin.
- `/api/v1/admin/me` returns `MODERATOR` for moderator.
- Moderator can access tournaments, series, leagues, and Polemica lookup endpoints.
- Moderator gets `403` for users, cards, packs, economy config, achievements, marketplace moderation, and broadcasts.

### Frontend

Run `npm run build` in `polemica-fantasy-admin` to verify TypeScript and Vite build.

Manual QA:

- Admin sees all sidebar sections and role badge `Admin`.
- Moderator sees only `Tournaments` and role badge `Moderator`.
- Moderator can manage tournaments, tournament players, series, leagues, and finalize series.
- Moderator receives `403` when opening hidden admin URLs directly.

## Deployment

Add moderator credentials to production `.env`:

```bash
ADMIN_MODERATOR_USERNAME=moderator_user
ADMIN_MODERATOR_PASSWORD=strong_secure_password_here
```

If either value is empty, no moderator Basic Auth user is created.

## Security Notes

- Backend authorization is enforced by Spring Security before controllers run.
- Frontend menu filtering is not a security boundary.
- Existing admin credentials and permissions are unchanged.
- Moderator credentials should be stored only in environment configuration, not in git.
