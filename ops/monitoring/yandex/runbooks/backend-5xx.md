# FantasyBackend5xxHigh

1. Confirm the alert has at least 20 requests in five minutes and is not a
   low-volume ratio artifact.
2. Check backend health and recent container logs without printing secrets.
3. Group failures by Spring route and status in PromQL; never expose raw URLs or
   request data in incident notes.
4. Check PostgreSQL health, Hikari pending connections, and Polemica upstream
   failures before restarting anything.
5. Roll back only the relevant backend release if the error increase correlates
   with a deployment.
