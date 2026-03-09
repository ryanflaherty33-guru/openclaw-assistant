#!/bin/bash
curl -L -X PUT -H 'Accept: application/vnd.github+json' -H "Authorization: Bearer $GITHUB_TOKEN" -H 'X-GitHub-Api-Version: 2022-11-28' https://api.github.com/repos/yuga-hashimoto/openclaw-assistant/topics -d '{"names":["android","voice-assistant","kotlin","jetpack-compose","wake-word","speech-recognition","text-to-speech","wear-os","ai-assistant","open-source"]}'
