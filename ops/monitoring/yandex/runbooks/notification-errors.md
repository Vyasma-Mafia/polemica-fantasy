# Notification delivery errors

1. Compare `outcome="sent"` and `outcome="error"` by fixed notification
   category. `bot_blocked`, user preferences, and global disablement are
   excluded from the delivery-error ratio; missing users count as errors.
2. Inspect backend logs for Telegram rate limits, transport failures, and retry
   results. Do not copy the bot token, chat IDs, or Telegram descriptions into
   metric labels.
3. Verify Telegram egress and the existing `wg-tg` route before changing bot
   code or notification settings.
4. Do not resend broadcasts automatically during recovery; user-visible replay
   requires an explicit operational decision.
