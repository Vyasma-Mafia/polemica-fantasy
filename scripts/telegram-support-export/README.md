# Telegram Support Export

Exports support messages from the Telegram support supergroup/forum with a
Telegram user account. This uses MTProto via Telethon, not the Bot API, so it can
read the same chat history that the logged-in account can see.

## Setup

1. Create Telegram API credentials at <https://my.telegram.org/apps>.
2. Install dependencies:

   ```bash
   cd scripts/telegram-support-export
   python3 -m venv .venv
   .venv/bin/pip install -r requirements.txt
   ```

3. Export credentials in the shell. Do not commit them.

   ```bash
   export TELEGRAM_API_ID=123456
   export TELEGRAM_API_HASH=your_api_hash
   export TELEGRAM_PHONE=+79990000000
   ```

## Usage

List visible chats:

```bash
scripts/telegram-support-export/.venv/bin/python \
  scripts/telegram-support-export/export_support_messages.py \
  --list-dialogs
```

Export forwarded user messages from the support forum:

```bash
scripts/telegram-support-export/.venv/bin/python \
  scripts/telegram-support-export/export_support_messages.py \
  --chat -1003620873111
```

Outputs are written to `scripts/telegram-support-export/out/`:

- `support-messages.jsonl` - normalized message records.
- `support-messages.csv` - spreadsheet-friendly copy.
- `support-requests.md` - first-pass split into bug, feature, question, noise.

The first run prompts for the Telegram login code and 2FA password if enabled.
Telethon stores a local `.session` file. The `.gitignore` in this directory
keeps credentials, sessions, virtualenvs, and exports out of git.

## Notes

By default the script keeps only forwarded messages. In the current support
flow these are the user messages that the bot forwarded into the support forum.
Use `--all-messages` to include admin replies and other group messages too.
