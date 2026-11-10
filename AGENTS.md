# Project rules

- Packages follow business areas (`employee`, `activity`, `attendance`, `calendar`); shared types go in `common`, web entry points in `web`, settings in `config`. Dependencies flow config → calendar → employee → attendance → activity → activity.cafeteria → activity.sharingshop → web; when a lower area needs upper data, define an interface in the lower area.
- No `var`. Name injected fields after their type (`leaveRequestRepository`). Method names start with a verb: get/find, is/can, validate, save/change/create.
- Lombok for plain constructors and getters; JPA no-arg constructors use `@NoArgsConstructor(access = AccessLevel.PROTECTED)`. Keep default `@Column`/`@JoinColumn` names. Surrogate keys are `Long` + `GenerationType.IDENTITY`.
- Persist by loading or creating the entity, changing it through domain methods, then `save()`. No native upserts, `EntityManager.persist()`, or `Isolation` settings; handle concurrency with row locks and DB unique constraints. Check duplicates with `existsBy...`; never infer business errors from DB exception messages.
- `@Query`: text block, uppercase keywords, one clause per line, parameter names match so `@Param` is omitted.
- Business rule violations throw `BusinessException` with an `ErrorCode`; each controller's `@ExceptionHandler` picks the redirect. Malformed input returns 400; unclassified DB errors use the shared 500 page. Invariant violations use `IllegalArgumentException`/`IllegalStateException`.
- URLs name resources; changes use PUT (save), DELETE (remove), POST (create or state transition). Forms POST with a hidden `_method`. No verbs like `add`/`remove`/`my-` in URLs.
- Keep code on one line when it reads well; when split, put one argument or component per line and close the parenthesis on its own line at the opening indent. Never cram statements onto one line. Do not reformat code the user has arranged.
- Do not generalize activity-specific mechanics (sharing shop lottery and time preference, cafeteria roster and exchange) into shared models.
- Comment only where code cannot explain itself; no Javadoc tags.
- Flyway V1 is the first production schema. Once deployed, add new versions with the same number to both `db/mysql` and `db/migration`.
- Commit messages are in English. Subjects start with `feat:` (new feature), `improve:` (improvement) or `fix:` (bug fix); docs, tests and chores take no prefix. Deploys publish these subjects as GitHub release notes.
- Business rules: docs/business-rules.md. DB principles: docs/persistence.md.
