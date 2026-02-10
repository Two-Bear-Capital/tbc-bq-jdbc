# IntelliJ AI Commit Message Prompt

Use this prompt in IntelliJ IDEA's AI commit message generator settings (Settings → Tools → AI Assistant → Commit Message Generation).

---

Generate commit messages following the Conventional Commits format: `<type>(<scope>): <description>`

**Types (choose the most specific):**
- `feat` - New feature or functionality
- `fix` - Bug fix
- `perf` - Performance improvement
- `docs` - Documentation changes
- `test` - Adding or updating tests
- `refactor` - Code refactoring (no functional changes)
- `style` - Code style/formatting changes
- `chore` - Maintenance tasks, dependencies, build changes
- `ci` - CI/CD pipeline changes
- `build` - Build system changes

**Scopes (optional but recommended):**
- `auth` - Authentication
- `metadata` - DatabaseMetaData
- `connection` - Connection management
- `resultset` - ResultSet operations
- `statement` - Statement/PreparedStatement
- `types` - Type mapping
- `cache` - Caching
- `session` - Session management
- `ci` - CI/CD
- `deps` - Dependencies
- `docs` - Documentation

**Format:**
- Keep the description concise and in imperative mood (e.g., "add" not "added")
- Start with lowercase
- No period at the end
- Maximum 72 characters for the first line

**Examples:**
- `feat(auth): add workforce identity federation support`
- `fix(metadata): correct precision for NUMERIC columns`
- `perf(cache): optimize parallel dataset loading with virtual threads`
- `docs(readme): add troubleshooting section for IntelliJ IDEA`
- `test(integration): add PreparedStatement parameter tests`
- `refactor(connection): simplify session lifecycle management`
- `chore(deps): upgrade BigQuery SDK to 2.40.0`

Analyze the staged changes and generate an appropriate commit message following this format.