# bulksender-ai-agent

Cloudflare Worker that proxies BulkSend AI agent chat requests to Gemini.

## Required secret

- `GEMINI_API_KEY`

## Local development

1. Copy `.dev.vars.example` to `.dev.vars`
2. Put your real Gemini API key in `.dev.vars`
3. Run `wrangler dev`

## Deploy

Set the production secret once:

```bash
wrangler secret put GEMINI_API_KEY
```

Then deploy:

```bash
wrangler deploy
```
