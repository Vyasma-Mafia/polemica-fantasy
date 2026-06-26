# Polemica Fantasy — Слияние карт (Design Document)

> **Статус:** DRAFT — продуктовая спецификация перед реализацией  
> Связанные документы: [`DESIGN-CARD-VALUE-AND-LEAGUES.md`](./DESIGN-CARD-VALUE-AND-LEAGUES.md), [`DESIGN-LEGENDARY-CARDS.md`](./DESIGN-LEGENDARY-CARDS.md), [`DESIGN-MARKETPLACE.md`](./DESIGN-MARKETPLACE.md)

---

## 1. Мотивация

Сейчас лишние карты можно переработать за фантики или продать на маркетплейсе.
Это полезно, но не даёт ощущения коллекционной прокачки: дубликаты слабых карт
не помогают развивать конкретного игрока, если рынок на них слабый.

**Цель:** дать пользователю способ превратить несколько младших карт одного
игрока в более редкую карту того же игрока.

### Что это даёт

- **Сжатие коллекции:** дубликаты перестают быть только "мусором" для recycle.
- **Прогрессия игрока:** пользователь целенаправленно собирает карты любимого или
  полезного `fantasy_player`.
- **Вторичный спрос на RARE:** разные перки RARE-карт становятся важны для
  будущего EPIC.
- **Card sink:** три экземпляра уходят из обращения, появляется один новый.
- **Контроль баланса:** слияние не создаёт LEGENDARY и не сбрасывает контрактную
  усталость.

---

## 2. Основная механика

### 2.1 Поддерживаемые уровни V1

| Операция | Вход | Результат |
|----------|------|-----------|
| `COMMON -> RARE` | 3 COMMON карты одного `fantasy_player` | 1 RARE карта того же `fantasy_player` |
| `RARE -> EPIC` | 3 RARE карты одного `fantasy_player` | 1 EPIC карта того же `fantasy_player` |

`EPIC -> LEGENDARY` остаётся отдельной существующей механикой legendary upgrade.
Слияние в LEGENDARY в V1 не добавляется.

При этом V1 **не ограничивает** дальнейший путь `RARE -> EPIC` через merge и
потом `EPIC -> LEGENDARY` через существующий legendary upgrade. Это ожидаемая
прогрессия: merge создаёт EPIC-материал, а legendary upgrade остаётся отдельной
платной операцией со своими проверками, стоимостью и выбором третьего перка.

### 2.2 Почему один и тот же игрок

Слияние должно быть прокачкой конкретного реального игрока, а не конвертером
трёх слабых карт в любую сильную карту. Поэтому все входные карты должны иметь
одинаковый `card_template.fantasy_player_id`, а результат сохраняет того же
`fantasy_player`.

Это создаёт понятную цель: собрать несколько карт нужного игрока, а не просто
накопить любую массу COMMON/RARE.

### 2.3 Создание результата

Слияние создаёт **новый экземпляр `user_card`**, а не меняет одну из входных карт
in-place.

| Поле результата | Правило |
|-----------------|---------|
| `telegram_user_id` | текущий пользователь |
| `card_template_id` | найденный или созданный шаблон того же игрока, новой редкости и выбранных перков |
| `source_card_pack_id` | `NULL` |
| `card_skin_id` | см. §6.4 |
| `crafted_by_telegram_user_id` | текущий пользователь |
| `acquired_at` | момент слияния |
| `uses_remaining` | см. §5 |
| `times_renewed` | см. §5 |
| `deleted_at` | `NULL` |

Входные карты после успешного слияния получают `deleted_at = now()` и скрываются
из активной коллекции. Это ближе к "поглощению" карт, чем к продаже или
переработке.

---

## 3. Перки

### 3.1 Базовая шкала редкостей

Слияние сохраняет текущую модель количества перков:

| Редкость | Перков |
|----------|--------|
| COMMON | 0 |
| RARE | 1 |
| EPIC | 2 |
| LEGENDARY | 3 |

Новые строки `card_template_perk` создаются с `bonus_points = NULL`, чтобы
использовать системный бонус из справочника `perk`.

### 3.2 COMMON -> RARE

У COMMON нет перков, поэтому для результата нужен новый перк.

Правило V1:

1. Backend собирает eligible-пул перков.
2. Пользователю показывается roll из 3 вариантов.
3. Пользователь выбирает 1 перк.
4. Создаётся RARE-шаблон того же игрока с выбранным перком.

Eligible-пул:

- по умолчанию `perk.can_appear_on_random_cards = true`;
- если позже появятся merge-specific настройки, они могут сузить этот пул;
- дубли в roll не допускаются.

Если eligible-пул пустой, операция недоступна как конфигурационная ошибка.
Если в пуле меньше 3 перков, показываются все доступные варианты.

### 3.3 RARE -> EPIC

Каждая RARE-карта имеет 1 перк. EPIC должен получить 2 уникальных перка.

Общее правило:

1. Собираем уникальные перки трёх входных RARE-карт.
2. Если уникальных перков минимум 2, пользователь выбирает 2 из них.
3. Если уникальный перк только 1, он фиксируется как первый перк EPIC.
4. Второй перк выбирается из roll 3 вариантов из eligible-пула, исключая уже
   выбранный перк.

Примеры:

| Входные RARE-перки | Поведение |
|--------------------|-----------|
| `A / B / C` | Пользователь выбирает любые 2 из `A, B, C` |
| `A / A / B` | Результат автоматически получает `A + B` |
| `A / A / A` | Результат получает `A + X`, где `X` выбирается из roll 3 вариантов без `A` |

Дублировать один и тот же перк на EPIC нельзя. `A + A` не поддерживается ни
технически, ни продуктово: двойной бонус одного события сложнее балансировать и
плохо читается в UI.

### 3.4 Почему не полный выбор из каталога

Полный выбор любого второго перка при `A / A / A` сделал бы одинаковые RARE
карты слишком сильным материалом. Roll из 3 вариантов решает UX-проблему
"мёртвой комбинации", но сохраняет ценность разных RARE-перков и не превращает
слияние в точную сборку идеального EPIC.

### 3.5 Защита от free reroll

Roll не должен превращаться в бесплатный перебор вариантов через повторный
preview.

Правило V1:

- roll привязан к точному набору входных `user_card_id`, операции и пользователю;
- `input_user_card_ids` нормализуются сортировкой и сохраняются как
  `input_set_hash`;
- если для того же пользователя, операции и набора входных карт уже есть
  неиспользованный preview, backend возвращает тот же `offered_perk_ids`, а не
  генерирует новый roll;
- смена переносимого скина не меняет roll: backend может обновить
  `selected_skin_source_user_card_id`, но оставляет прежние `offered_perk_ids`;
- истечение preview не даёт новый roll для тех же входных карт: повторный preview
  продлевает окно подтверждения с теми же `offered_perk_ids`;
- новый roll возможен только если пользователь меняет хотя бы одну карту-материал
  или старый preview был успешно consumed успешным merge.

Если eligible-пул изменился после создания preview и ранее предложенный перк
стал недоступен, confirm отклоняется как конфигурационная ошибка без списания
карт. Пользователь должен собрать новый набор материалов или дождаться исправления
конфигурации.

---

## 4. Предусловия и запреты

Все входные карты должны:

- принадлежать текущему пользователю;
- быть не `deleted`;
- иметь одинаковую редкость;
- иметь одинаковый `fantasy_player`;
- соответствовать поддерживаемой операции V1 (`COMMON -> RARE` или `RARE -> EPIC`);
- не участвовать в команде незавершённой серии;
- не иметь ACTIVE-листинг на marketplace;
- иметь `uses_remaining > 0`.

Дополнительные правила:

- входные `user_card_id` должны быть уникальны;
- нельзя смешивать скины без явного правила переноса, см. §6.4;
- нельзя сливать карты разных пользователей;
- нельзя сливать LEGENDARY;
- нельзя сливать EPIC через эту механику;
- нельзя сливать карту, которая уже была soft-deleted через recycle или прошлое
  слияние.

Ошибки должны быть пользовательски понятными, например:

| Нарушение | Сообщение |
|-----------|-----------|
| Карта не найдена или не принадлежит пользователю | `Card not found or not owned` |
| Карты разных игроков | `Cards must belong to the same player` |
| Карты разных редкостей | `Cards must have the same rarity` |
| Неподдерживаемая редкость | `Only COMMON and RARE cards can be merged` |
| Карта в незавершённой серии | `Cannot merge a card in an active team` |
| Карта на marketplace | `Cannot merge a card listed on the marketplace` |
| Истёкший контракт | `Only cards with remaining uses can be merged` |

---

## 5. Контракты и переподписания

Слияние не должно быть способом сбросить усталость карты.

### 5.1 `times_renewed`

Результат наследует максимальное число переподписаний среди входных карт:

```text
result.times_renewed = max(input.times_renewed)
```

Слияние **не считается переподписанием**, поэтому `times_renewed` не увеличивается
само по себе. Но пользователь не получает новую "чистую" карту, если использовал
старые переподписанные экземпляры.

### 5.2 `uses_remaining`

Результат получает uses по формуле:

```text
result.uses_remaining = min(baseUses(result.rarity), sum(input.uses_remaining))
```

Где `baseUses` берётся из `economy_config.card.uses.*`.

Примеры при текущих дефолтах:

| Операция | Вход uses | baseUses результата | Результат |
|----------|-----------|---------------------|-----------|
| COMMON -> RARE | `2 + 2 + 2 = 6` | `3` | `3` |
| COMMON -> RARE | `1 + 1 + 1 = 3` | `3` | `3` |
| RARE -> EPIC | `3 + 3 + 3 = 9` | `4` | `4` |
| RARE -> EPIC | `1 + 1 + 1 = 3` | `4` | `3` |

Так слияние нормальных свежих карт даёт полноценный контракт новой редкости, но
не превращает три почти истёкшие карты в полностью свежую EPIC, если суммарной
энергии не хватает.

### 5.3 Лимит продлений

Если `max(input.times_renewed) >= renewal.max_times`, слияние разрешено, но
результат тоже будет на лимите продлений. Его можно доиграть оставшимися uses
или переработать, но нельзя будет продлить или перепродать через marketplace
после исчерпания uses.

Это сохраняет инвариант: слияние не продлевает жизненный цикл карты сверх
существующих контрактных ограничений.

---

## 6. Экономика и баланс

### 6.1 Стоимость операции

V1 можно запустить без дополнительной платы в фантиках: основной cost — это
уничтожение трёх карт ради одной.

Опциональный future-key, если баланс потребует sink:

| Ключ | Дефолт | Описание |
|------|--------|----------|
| `card.merge.cost.COMMON_TO_RARE` | `0` | Стоимость слияния 3 COMMON в RARE |
| `card.merge.cost.RARE_TO_EPIC` | `0` | Стоимость слияния 3 RARE в EPIC |

Если ключи вводятся сразу, админка Economy должна показывать их рядом с
контрактами карт.

### 6.2 Влияние на card value

Слияние обычно снижает суммарную portfolio value:

- 3 COMMON по 25 = 75 -> RARE примерно 50;
- 3 RARE по 50 = 150 -> EPIC примерно 100.

Это нормально: пользователь меняет ширину коллекции на более сильный точечный
актив. В бюджетной лиге такая карта не всегда лучше, потому что её value выше.

### 6.3 Влияние на marketplace

Ожидаемый эффект:

- растёт спрос на дубликаты нужного игрока;
- RARE с разными перками становятся дороже, потому что дают контроль над EPIC;
- RARE с одинаковыми перками не становятся бесполезными из-за правила `A/A/A`;
- старые карты с большим `times_renewed` менее ценны как материал, потому что
  результат наследует max `times_renewed`.

Нужно показывать `timesRenewed` и `usesRemaining` в merge UI перед подтверждением,
иначе пользователь может случайно сделать EPIC с плохим контрактом.

Результат merge считается новым экземпляром `user_card` для marketplace
provenance. История владельцев входных карт не переносится на результат и не
участвует в запрете "нельзя купить карту, которой когда-либо владел". Полная
связь с материалами остаётся в `user_card_merge` / `user_card_merge_input` для
поддержки и аналитики.

### 6.4 Скины

Скин привязан к экземпляру `user_card`, поэтому при 3->1 нужен явный перенос.

V1-правило:

- если среди входных карт нет скинов, результат без скина;
- если ровно одна входная карта со скином, этот скин переносится на результат;
- если несколько входных карт со скинами, пользователь выбирает один скин для
  результата, остальные скины сгорают вместе с материалами;
- перед подтверждением UI явно показывает, какие скины будут потеряны.

Скины не влияют на перки, uses, value или стоимость операции.

---

## 7. История и аудит

### 7.1 Почему нужна отдельная история

`user_card_ownership_history` отвечает на вопрос "кто владел этим экземпляром и
как он был получен". Для слияния этого недостаточно: нужно знать, из каких карт
был создан новый экземпляр и какие параметры были у материалов.

### 7.2 Новые acquisition type

Добавить `CardAcquisitionType.CARD_MERGE`.

Лейбл в истории: `собрана из карт`.

Для результата записывается строка ownership history:

| Поле | Значение |
|------|----------|
| `user_card_id` | новый результат |
| `telegram_user_id` | текущий пользователь |
| `acquired_at` | момент слияния |
| `acquisition_type` | `CARD_MERGE` |

### 7.3 Новые таблицы

Рекомендуемая модель:

```sql
CREATE TABLE user_card_merge (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL REFERENCES telegram_user(id),
    preview_id BIGINT UNIQUE,
    result_user_card_id BIGINT NOT NULL UNIQUE REFERENCES user_card(id),
    source_rarity VARCHAR(32) NOT NULL,
    result_rarity VARCHAR(32) NOT NULL,
    fantasy_player_id BIGINT NOT NULL REFERENCES fantasy_player(id),
    selected_perk_ids JSONB NOT NULL,
    offered_perk_ids JSONB,
    cost_fantiki BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE user_card_merge_input (
    id BIGSERIAL PRIMARY KEY,
    merge_id BIGINT NOT NULL REFERENCES user_card_merge(id) ON DELETE CASCADE,
    input_user_card_id BIGINT NOT NULL REFERENCES user_card(id),
    input_card_template_id BIGINT NOT NULL REFERENCES card_template(id),
    input_rarity VARCHAR(32) NOT NULL,
    input_perk_ids JSONB NOT NULL,
    input_uses_remaining INT NOT NULL,
    input_times_renewed INT NOT NULL,
    input_skin_code VARCHAR(64)
);
```

`selected_perk_ids` фиксирует результат, а `offered_perk_ids` фиксирует roll,
если пользователь выбирал из случайных вариантов. Это нужно для поддержки,
аналитики и разборов спорных случаев.

Если используется `user_card_merge_preview`, `preview_id` связывает successful
confirm с исходным preview. Это позволяет сделать confirm идемпотентным: повтор
того же confirm после сетевого дубля может вернуть уже созданный result card. FK
на preview можно добавить в миграции после создания обеих таблиц или создать
preview-таблицу раньше `user_card_merge`.

### 7.4 Прошлые команды и скоринг

Старые `fantasy_team_card` и score breakdown остаются привязаны к входным
`user_card`. Soft-delete не должен ломать историю прошлых серий.

Именно поэтому результат создаётся новым `user_card`, а не меняет `card_template`
одной из входных карт.

---

## 8. Пользовательский UX в TMA

### 8.1 Точка входа

Основной экран: `/cards/merge`.

Точки входа:

- отдельная кнопка **Слияние** в коллекции рядом с фильтрами/режимами просмотра;
- действие **Слияние** в модалке карты, если у пользователя есть потенциальные
  материалы того же `fantasy_player`;
- CTA из `/whats-new` после релиза;
- ссылка из `/help` в раздел коллекции.

Рекомендуемый V1 flow:

1. Пользователь открывает экран слияния.
2. Выбирает игрока, по которому есть доступные комбинации.
3. Выбирает операцию `COMMON -> RARE` или `RARE -> EPIC`.
4. Выбирает 3 карты-материала.
5. При необходимости снимает материалы с marketplace прямо из этого flow.
6. Выбирает перк, если операция требует выбора.
7. Выбирает переносимый скин, если материалов со скинами несколько.
8. Видит preview результата: игрок, редкость, перки, uses, renewals, value,
   пригодность для BUDGET, скин и список потерянных материалов.
9. Подтверждает.
10. Получает success state с новой картой и CTA в коллекцию / legendary upgrade
    для EPIC-результата.

### 8.2 Экран выбора игрока и операции

Первый экран группирует материалы по `fantasy_player`.

Карточка игрока показывает:

- фото/ник игрока;
- сколько доступно COMMON и RARE материалов;
- сколько карт заблокировано и почему: `в команде`, `на продаже`, `0 uses`;
- доступные операции: `COMMON -> RARE`, `RARE -> EPIC`;
- preview результата по редкости: `RARE` или `EPIC`.

Сортировка:

1. игроки с доступной операцией `RARE -> EPIC`;
2. игроки с доступной операцией `COMMON -> RARE`;
3. игроки, которым не хватает 1 карты до операции;
4. остальные игроки с заблокированными картами.

Если доступных комбинаций нет, empty state должен объяснить не только "нужны 3
карты одного игрока", но и показать ближайшие цели: игроки, у которых есть 2/3
материалов, и причины блокировки третьей карты.

### 8.3 Выбор материалов

После выбора игрока и операции пользователь видит 3 fixed slots и список карт
этого игрока нужной редкости.

Карточка материала показывает:

- фото, редкость, скин, перки;
- `usesRemaining`;
- `timesRenewed/maxRenewals`;
- `value`;
- статус marketplace / команды / expired;
- для RARE — крупный чип перка, потому что он влияет на EPIC.

Eligible cards можно выбрать в слот. Disabled cards остаются видимыми с причиной:

| Причина | UI |
|---------|----|
| В незавершённой команде | Disabled, текст `В составе на серию` |
| ACTIVE marketplace listing | Disabled, текст `На продаже`, кнопка `Снять с продажи` |
| `usesRemaining = 0` | Disabled, текст `Контракт истёк` |
| `deleted_at != NULL` | Не показывать в обычном списке; можно учитывать только в debug/support |
| Другая редкость/игрок | Не показывать на этом шаге |

Кнопка `Снять с продажи` вызывает существующее снятие ACTIVE-листинга. После
успеха экран обновляет merge options и оставляет пользователя в текущем flow.
Merge сам не отменяет листинг автоматически.

Рекомендация по автосортировке материалов внутри списка:

1. selectable cards;
2. карты без скина выше карт со скином;
3. меньший `usesRemaining` выше, если сумма выбранных uses всё равно даёт полный
   контракт результата;
4. меньший `timesRenewed` выше;
5. затем по `acquiredAt`.

Автовыбор 3 материалов в V1 не нужен: пользователь должен явно выбрать карты,
потому что операция необратимо уничтожает материалы.

### 8.4 Выбор перков

`COMMON -> RARE`:

- показываем до 3 rolled перков;
- каждый вариант содержит название, описание, бонус, applicable roles;
- если preview уже существовал, показываем те же варианты и текст `Варианты
  зафиксированы для выбранных карт`.

`RARE -> EPIC`:

- `A/B/C`: показываем 3 source-перка с привязкой к материалам; пользователь
  выбирает 2;
- `A/A/B`: перки `A+B` выбираются автоматически, UI показывает, из каких карт они
  пришли;
- `A/A/A`: `A` фиксируется, второй перк выбирается из rolled options без `A`;
- дубли одного перка всегда визуально схлопываются, но рядом показывается
  количество источников, например `sniper x3`.

### 8.5 Preview и подтверждение

Preview должен быть отдельным финальным шагом, а не маленьким текстом под
кнопкой. Он показывает:

- результат: игрок, редкость, перки, скин;
- `usesRemaining` результата и формулу `min(baseUses, sum(inputUses))`;
- `timesRenewed = max(inputs)` и предупреждение, если результат на лимите;
- `value` до/после: сумма value материалов -> value результата;
- пригодность для BUDGET при текущем стандартном `valueCap`, если value доступен;
- список материалов, которые исчезнут из коллекции;
- скины, которые будут потеряны;
- marketplace статус результата: можно ли будет выставить после merge с учётом
  `timesRenewed < maxRenewals`;
- для EPIC-результата — нейтральную подсказку, что дальнейший LEGENDARY upgrade
  остаётся отдельной платной операцией.

Требования к подтверждению:

- primary button `Собрать карту`;
- secondary button `Назад к материалам`;
- checkbox не нужен, но кнопка должна быть disabled до загрузки preview;
- при плохом контракте (`timesRenewed = maxRenewals` или result uses ниже base)
  показывать warning непосредственно над кнопкой.

### 8.6 Success state

После успешного merge:

- показать новую карту крупно;
- показать краткий summary: `3 COMMON -> RARE` или `3 RARE -> EPIC`;
- CTA `В коллекцию`;
- CTA `Собрать ещё`, если есть доступные комбинации;
- для EPIC-результата CTA `Улучшить до LEGENDARY`, если legendary upgrade
  доступен по текущим правилам.

### 8.7 Ошибки и stale states

| Состояние | Поведение |
|-----------|-----------|
| Нет доступных комбинаций | Empty state с ближайшими 2/3 целями и причинами блокировки |
| Есть COMMON-комбо | Показать игроков и количество доступных COMMON |
| Есть RARE-комбо | Показать игроков, перки RARE и предупреждение про выбранные перки |
| Карта недоступна | Disabled с причиной: команда, marketplace, 0 uses, deleted |
| Карта на marketplace | Disabled + `Снять с продажи` |
| Несколько скинов | Явный выбор переносимого скина |
| Плохой контракт | Warning: результат наследует `timesRenewed = N/max` |
| Preview истёк | Reopen preview с тем же roll для тех же материалов |
| Материал изменился после preview | Error state, вернуться к выбору материалов |
| Ошибка roll/config | Error state без списания карт |
| Confirm double tap | Вторая попытка возвращает уже созданный результат или понятный consumed-state |

### 8.8 Copy

Основные тексты:

- `Слияние карт`
- `Выберите 3 карты одного игрока`
- `Результат: RARE {nickname}`
- `Результат: EPIC {nickname}`
- `Контракт результата: {uses} использ., переподписаний {timesRenewed}/{max}`
- `Эти карты исчезнут из коллекции`
- `Скин будет перенесён`
- `Остальные скины будут потеряны`
- `Эта карта на продаже`
- `Снять с продажи`
- `Варианты перков зафиксированы для выбранных карт`
- `Ценность коллекции уменьшится: {before} -> {after}`
- `Можно будет улучшить до LEGENDARY отдельно`
- `Собрать карту`

Текст для `A/A/A`:

> У всех трёх RARE-карт одинаковый перк. Он сохранится, а второй перк можно
> выбрать из предложенных вариантов.

---

## 9. Admin и операционные сценарии

V1 не требует admin workflow для ручного слияния за пользователя. Но read-only
поддержка merge нужна сразу, потому что операция необратимая и использует roll.

Минимум для V1:

- видеть acquisition type `CARD_MERGE` в истории карты;
- при просмотре пользователя/карты иметь возможность понять источник результата;
- в детализации карты-результата показать merge inputs, выбранные/предложенные
  перки, сожжённые скины, cost, timestamp и preview id.

Опционально для админки:

- фильтр пользователей по числу merge-событий;
- таблица merge history для поддержки;
- economy keys для стоимости слияния, если они введены.

---

## 10. Backend contract

### 10.1 User API

Предлагаемый контракт:

```http
GET /api/v1/cards/merge/options
```

Возвращает доступные комбинации и причины недоступности.
Должен включать не только selectable cards, но и disabled cards того же игрока и
редкости с причиной блокировки, чтобы TMA могла показать путь к исправлению.

```json
{
  "groups": [
    {
      "fantasyPlayerId": 10,
      "nickname": "Player",
      "photoUrl": "https://...",
      "operations": [
        {
          "operation": "COMMON_TO_RARE",
          "sourceRarity": "COMMON",
          "resultRarity": "RARE",
          "availableCards": [],
          "blockedCards": [
            {
              "userCardId": 55,
              "reason": "MARKETPLACE_ACTIVE",
              "listingId": 9001,
              "canCancelListing": true
            }
          ],
          "eligible": true
        }
      ]
    }
  ]
}
```

```http
POST /api/v1/cards/merge/preview
```

Тело:

```json
{
  "operation": "RARE_TO_EPIC",
  "inputUserCardIds": [101, 102, 103],
  "selectedSkinSourceUserCardId": 101
}
```

Ответ:

```json
{
  "operation": "RARE_TO_EPIC",
  "previewId": 42,
  "expiresAt": "2026-06-25T12:15:00Z",
  "sameRollForInputSet": true,
  "fixedPerkIds": ["sniper"],
  "selectablePerks": [
    { "id": "voteForBlack", "name": "..." },
    { "id": "sheriffCheck", "name": "..." },
    { "id": "donCheck", "name": "..." }
  ],
  "requiredSelections": 1,
  "result": {
    "fantasyPlayerId": 10,
    "rarity": "EPIC",
    "usesRemaining": 4,
    "timesRenewed": 0,
    "skinCode": "tournament_gold"
  },
  "valueBefore": 150,
  "valueAfter": 100,
  "warnings": [
    { "code": "PORTFOLIO_VALUE_DECREASE", "message": "Ценность коллекции уменьшится: 150 -> 100" }
  ]
}
```

```http
POST /api/v1/cards/merge/confirm
```

Тело:

```json
{
  "operation": "RARE_TO_EPIC",
  "inputUserCardIds": [101, 102, 103],
  "selectedPerkIds": ["sniper", "voteForBlack"],
  "selectedSkinSourceUserCardId": 101,
  "previewId": 42
}
```

Ответ:

```json
{
  "card": { "...": "UserCardItemDto" },
  "spentFantiki": 0,
  "newBalance": 1200
}
```

### 10.2 Preview и анти-reroll

Если preview генерирует random roll, confirm должен защищаться и от подмены
выбора, и от бесплатного reroll.

V1 использует таблицу `user_card_merge_preview`.

Поля:

- `telegram_user_id`;
- `input_user_card_ids`;
- `input_set_hash`;
- `operation`;
- `fixed_perk_ids`;
- `offered_perk_ids`;
- `selected_skin_source_user_card_id`;
- `expires_at`;
- `consumed_at`;
- `result_user_card_id`.

Индекс:

```sql
CREATE UNIQUE INDEX user_card_merge_preview_active_input_set
    ON user_card_merge_preview (telegram_user_id, operation, input_set_hash)
    WHERE consumed_at IS NULL;
```

Поведение:

- preview для того же `input_set_hash` возвращает тот же roll;
- истёкший preview для того же набора не reroll'ится, а получает новое
  `expires_at` с прежними `offered_perk_ids`;
- смена скина не меняет `input_set_hash` и не reroll'ит перки;
- confirm принимает `previewId`, а не произвольный token;
- successful confirm записывает `consumed_at` и `result_user_card_id`;
- повтор confirm consumed preview возвращает result card, если входной payload
  совпадает с исходным preview;
- cleanup может удалять только consumed previews и previews, где один из input
  cards уже deleted / сменил владельца.

Confirm принимает `previewId`, блокирует preview row и входные карты
`PESSIMISTIC_WRITE`, затем повторно валидирует все условия.

---

## 11. Транзакции и блокировки

Confirm должен быть одной короткой транзакцией:

1. Заблокировать preview row.
2. Заблокировать входные `user_card` через `PESSIMISTIC_WRITE`.
3. Повторно проверить ownership, `deleted_at`, uses, marketplace, команды.
4. Проверить выбранные перки против preview.
5. При необходимости списать cost fantiki.
6. Найти или создать `card_template`.
7. Создать result `user_card`.
8. Soft-delete входные карты.
9. Записать `user_card_merge` и `user_card_merge_input`.
10. Записать `user_card_ownership_history`.
11. Опубликовать achievement/product events после commit.

HTTP-вызовов к Polemica нет, поэтому внешних сетевых операций внутри транзакции
не требуется.

---

## 12. Analytics и достижения

Product/analytics events в V1 записываются в `product_event`:

- `CARD_MERGE_PREVIEW_CREATED`;
- `CARD_MERGE_PREVIEW_REUSED`;
- `CARD_MERGE_CONFIRMED`;
- `CARD_MERGE_FAILED`.

Рекомендуемые `subject_type`:

- preview events: `CARD_MERGE_PREVIEW`, `subject_id = previewId`;
- confirm events: `CARD_MERGE`, `subject_id = mergeId`;
- failed events: `CARD_MERGE_PREVIEW` или `CARD_MERGE`, если id уже известен.

Event metadata должен включать: `operation`, `sourceRarity`, `resultRarity`,
`fantasyPlayerId`, `inputUserCardIds`, `inputUsesSum`, `resultUsesRemaining`,
`inputMaxTimesRenewed`, `resultTimesRenewed`, `selectedPerkIds`,
`offeredPerkIds`, `skinTransferred`, `skinsBurnedCount`, `costFantiki`,
`valueBefore`, `valueAfter`, `failureCode` для failed events.

### 12.1 Достижения V1

Добавить condition types:

| Condition | Расчёт |
|-----------|--------|
| `CARD_MERGES` | `COUNT(*) FROM user_card_merge WHERE telegram_user_id = ? AND created_at >= trackingStartedAt` |
| `CARD_MERGE_EPIC_RESULTS` | то же, но `result_rarity = 'EPIC'` |
| `CARD_MERGE_UNIQUE_PLAYERS` | `COUNT(DISTINCT fantasy_player_id)` по `user_card_merge` |

Seed достижений:

| Code | Condition | Target | Title | Description | Rarity | Rewards |
|------|-----------|--------|-------|-------------|--------|---------|
| `card_merge_1` | `CARD_MERGES` | 1 | `Первая сборка` | `Выполнить первое слияние карт` | COMMON | `FANTIKI 25` |
| `card_merge_10` | `CARD_MERGES` | 10 | `Мастерская коллекции` | `Выполнить 10 слияний карт` | RARE | `BADGE_STYLE card_merge`, `CARD_CHOICE_ROLL COMMON x2 из 5` |
| `card_merge_epic_1` | `CARD_MERGE_EPIC_RESULTS` | 1 | `Эпик из деталей` | `Собрать первую EPIC-карту через слияние` | RARE | `CARD_CHOICE_ROLL RARE x2 из 5` |
| `card_merge_epic_5` | `CARD_MERGE_EPIC_RESULTS` | 5 | `Эпический сборщик` | `Собрать 5 EPIC-карт через слияние` | EPIC | `BADGE_STYLE epic_crafter`, `CARD_CHOICE_ROLL EPIC x1 из 3` |
| `card_merge_players_5` | `CARD_MERGE_UNIQUE_PLAYERS` | 5 | `Ростерная мастерская` | `Собрать карты через слияние для 5 разных игроков` | RARE | `FANTIKI 75` |

Все достижения используют `history_policy = FROM_ACHIEVEMENTS_LAUNCH` или дату
релиза merge, чтобы не пытаться восстанавливать исторические события.

После successful confirm backend:

- записывает `product_event` с `event_type = CARD_MERGE_CONFIRMED`,
  `subject_type = CARD_MERGE`, `subject_id = mergeId`;
- публикует `AchievementProgressEvent(COLLECTION_CHANGED, user)`, чтобы текущий
  achievement listener пересчитал новые condition types вместе с остальной
  коллекцией.

---

## 13. Help, whats-new и коммуникация

### 13.1 `/help`

Добавить раздел **Слияние карт** в блок экономики/коллекции:

- `3 COMMON одного игрока -> 1 RARE того же игрока`;
- `3 RARE одного игрока -> 1 EPIC того же игрока`;
- EPIC после merge можно отдельно улучшить до LEGENDARY через legendary upgrade;
- материалы исчезают из коллекции навсегда;
- результат наследует худшую контрактную усталость:
  `timesRenewed = max(inputs)`;
- uses результата: `min(baseUses(resultRarity), sum(inputUses))`;
- value коллекции обычно уменьшается: пользователь меняет ширину коллекции на
  более сильную точечную карту;
- карты на marketplace нужно сначала снять с продажи;
- если у нескольких материалов есть скины, переносится только выбранный скин,
  остальные сгорают;
- варианты rolled-перков фиксируются для выбранных материалов, перезапуск preview
  не даёт бесплатный reroll.

### 13.2 `/whats-new`

Release note:

| Поле | Значение |
|------|----------|
| Title | `Слияние карт` |
| Body | `Теперь дубликаты одного игрока можно собрать в карту выше редкостью: 3 COMMON -> RARE или 3 RARE -> EPIC. Перед подтверждением показываем контракт, перки, ценность и потерю скинов.` |
| CTA | `Открыть слияние` -> `/cards/merge` |

Дополнительно можно подготовить draft product campaign для активных пользователей
с 2+ дубликатами одного игрока, но не отправлять её миграцией автоматически.

---

## 14. Acceptance Criteria

- Пользователь может слить 3 COMMON одного игрока в RARE того же игрока.
- Для COMMON -> RARE пользователь выбирает 1 перк из roll до 3 вариантов.
- Пользователь может слить 3 RARE одного игрока в EPIC того же игрока.
- Для RARE -> EPIC:
  - `A/B/C` даёт выбор 2 из 3;
  - `A/A/B` автоматически даёт `A+B`;
  - `A/A/A` даёт `A + 1 из roll без A`.
- Результат создаётся как новый `user_card`; входные карты soft-delete.
- Прошлые команды и score breakdown не ломаются.
- `timesRenewed` результата равен max входных карт.
- `usesRemaining` результата равен `min(baseUses(resultRarity), sum(inputUses))`.
- Нельзя сливать карты в незавершённых командах, ACTIVE-листингах, deleted или
  expired cards.
- История результата показывает acquisition type `CARD_MERGE`.
- Audit tables позволяют восстановить входные карты и предложенные/выбранные
  перки.
- TMA показывает preview результата, потерю входных карт, перенос/потерю скинов
  и контракт результата до подтверждения.
- Повторный preview для тех же материалов не даёт новый roll.
- Карта в ACTIVE marketplace listing недоступна для merge, но UI даёт кнопку
  `Снять с продажи` и возвращает пользователя в merge flow после успеха.
- `/help` объясняет merge, контракты, value loss, скины и отличие от legendary
  upgrade.
- `/whats-new` содержит release note с CTA на `/cards/merge`.
- Достижения V1 для merge seeded и пересчитываются после successful confirm.

---

## 15. Non-goals V1

- Слияние EPIC в LEGENDARY.
- Слияние карт разных игроков.
- Слияние карт разных редкостей.
- Дублирование одного перка на EPIC.
- Полный ручной выбор любого перка из каталога.
- Админский ручной merge за пользователя.
- Автоматическая компенсация за потерянные скины.
- Изменение правил marketplace contract reissue.
- Запрет последующего legendary upgrade для EPIC, собранной через merge.
- Перенос ownership history входных карт на результат merge.

---

## 16. Open Questions

- Нужна ли плата в фантиках за `RARE -> EPIC` уже в V1, или достаточно card
  sink?
- Должны ли карты с `uses_remaining = 0` быть материалом за отдельную доплату,
  или запрет expired cards оставить постоянным?
- Нужно ли ограничивать количество merge operations в день, если появятся
  злоупотребления через мультиаккаунты?
- Нужен ли отдельный merge-specific pool перков вместо общего random-card pool?
